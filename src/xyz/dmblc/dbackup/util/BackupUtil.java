package xyz.dmblc.dbackup.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;

import com.google.common.io.Files;

import xyz.dmblc.dbackup.dBackup;

/*
	Copyright 2020 dmblc
	
	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at
	
	    http://www.apache.org/licenses/LICENSE-2.0
	    
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.

*/

public class BackupUtil {

	static boolean backingUp = false;

	// delete old backups (when limit reached)
	private static void checkMaxBackups(boolean doAll) {
		int numBackups = 0;
		int maxBackups;
		SortedMap<Long, File> mapBackups = new TreeMap<>(); // oldest files to newest

		if (!doAll) {
			maxBackups = dBackup.getPlugin().getMaxBackups();
			for (File f : dBackup.getPlugin().getBackupPath().listFiles()) {
				if (f.getName().endsWith(".zip") && !f.getName().startsWith("FULL-")) {
					numBackups++;
					mapBackups.put(f.lastModified(), f);
				}
			}
		} else {
			maxBackups = dBackup.getPlugin().getMaxFullBackups();
			for (File f : dBackup.getPlugin().getBackupPath().listFiles()) {
				if (f.getName().endsWith(".zip") && f.getName().startsWith("FULL-")) {
					numBackups++;
					mapBackups.put(f.lastModified(), f);
				}
			}
		}

		while (numBackups-- >= maxBackups) {
			dBackup.getPlugin().getLogger()
					.info("Deleting old backup " + mapBackups.get(mapBackups.firstKey()).getName() + "...");
			mapBackups.get(mapBackups.firstKey()).delete();
			mapBackups.remove(mapBackups.firstKey());
		}
	}

	// actually do the backup
	// run async plz
	public static void doBackup(boolean doAll) {
		if (backingUp) {
			dBackup.getPlugin().getLogger().info("Backup already in progress, skipping.");
			return;
		}

		dBackup.getPlugin().getLogger().info("Starting backup...");
		backingUp = true;

		List<File> tempIgnore = new ArrayList<>();

		File currentWorkingDirectory = new File(Paths.get(".").toAbsolutePath().normalize().toString());

		try {
			// find plugin data to ignore
			for (File f : new File("plugins").listFiles()) {
				if ((!dBackup.getPlugin().isBackupPluginJars() && f.getName().endsWith(".jar"))
						|| (!dBackup.getPlugin().isBackupPluginConfs() && f.isDirectory())) {
					if (dBackup.getPlugin().isDebugMode()) {
						dBackup.getPlugin().getLogger().info("Ignoring file " + f.getName() + "...");
					}
					tempIgnore.add(f);
					dBackup.getPlugin().getIgnoredFiles().add(f);
				}
			}

			// find unloaded worlds to ignore
			if (!doAll) {
				for (World w : Bukkit.getWorlds()) {
					if (dBackup.getPlugin().getLoadedWorlds().contains(w)) {
						continue;
					} else if (w.getPlayerCount() == 0 && !dBackup.getPlugin().getLoadedWorlds().contains(w)) {
						if (dBackup.getPlugin().isDebugMode()) {
							dBackup.getPlugin().getLogger().info("Ignoring world " + w.getName() + "...");
						}
						tempIgnore.add(w.getWorldFolder());
						dBackup.getPlugin().getIgnoredFiles().add(w.getWorldFolder());
					}
				}
			}

			// delete old backups
			checkMaxBackups(doAll);

			// zip
			String fileName = dBackup.getPlugin().getBackupFormat().replace("{DATE}",
					new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date()));

			if (!doAll) {
				for (World w : Bukkit.getWorlds()) {
					AtomicBoolean saved = new AtomicBoolean(false);
					Bukkit.getScheduler().runTask(dBackup.getPlugin(), () -> {
						w.save();
						saved.set(true);
					});

					while (!saved.get())
						Thread.sleep(500);
					
					w.setAutoSave(false); // make sure autosave doesn't screw everything over
					
					for (ChunkSnapshot c : dBackup.getLoadedChunks()) {
						if (!Bukkit.getWorld(c.getWorldName()).equals(w)) {
							return;
						}

						int regionX = c.getX() >> 5;
						int regionZ = c.getZ() >> 5;

						String regionName = "r." + regionX + "." + regionZ + ".mcr";

						File worldFolder = w.getWorldFolder();

						String worldPath = Paths.get(currentWorkingDirectory.toURI())
								.relativize(Paths.get(worldFolder.toURI())).toString();

						if (worldPath.endsWith("/.")) {// 1.16 world folders end with /. for some reason
							worldPath = worldPath.substring(0, worldPath.length() - 2);
							worldFolder = new File(worldPath);
						}
						
						dBackup.getPlugin().getLogger().info("Backing up " + worldPath + "...");

						File sourceFile = new File(w.getWorldFolder() + "/" + "region/" + regionName);
						File destFile = new File(
								dBackup.getPlugin().getBackupPath() + "/" + worldPath + "/" + "region/" + regionName);

						Files.copy(sourceFile, destFile);

						// dfs all other files
						dBackup.getPlugin().getLogger().info("Backing up other files...");
					}
					w.setAutoSave(true);
				}
			} else if (doAll) {
				fileName = "FULL-" + fileName;

				FileOutputStream fos = new FileOutputStream(currentWorkingDirectory + "/" + fileName + ".zip");
				ZipOutputStream zipOut = new ZipOutputStream(fos);

				// backup worlds first
				for (World w : Bukkit.getWorlds()) {
					File worldFolder = w.getWorldFolder();

					String worldPath = Paths.get(currentWorkingDirectory.toURI())
							.relativize(Paths.get(worldFolder.toURI())).toString();
					if (worldPath.endsWith("/.")) {// 1.16 world folders end with /. for some reason
						worldPath = worldPath.substring(0, worldPath.length() - 2);
						worldFolder = new File(worldPath);
					}

					// check if world is in ignored list
					boolean skipWorld = false;
					for (File f : dBackup.getPlugin().getIgnoredFiles()) {
						if (f.getAbsolutePath().equals(worldFolder.getAbsolutePath())) {
							skipWorld = true;
							break;
						}
					}
					if (skipWorld)
						continue;

					AtomicBoolean saved = new AtomicBoolean(false);
					Bukkit.getScheduler().runTask(dBackup.getPlugin(), () -> {
						w.save();
						saved.set(true);
					});

					while (!saved.get())
						Thread.sleep(500);

					w.setAutoSave(false); // make sure autosave doesn't screw everything over
					dBackup.getPlugin().getLogger().info("Backing up " + worldPath + "...");
					zipFile(worldFolder, worldPath, zipOut);
					w.setAutoSave(true);

					// ignore in dfs
					tempIgnore.add(worldFolder);
					dBackup.getPlugin().ignoredFiles.add(worldFolder);
				}

				// dfs all other files
				dBackup.getPlugin().getLogger().info("Backing up other files...");
				zipFile(currentWorkingDirectory, "", zipOut);
				zipOut.close();
				fos.close();

				dBackup.getPlugin().getLogger().info("Moving zip...");
				File result = new File(currentWorkingDirectory + "/" + fileName + ".zip");
				Files.copy(result, dBackup.getPlugin().getBackupPath());
				result.delete();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			for (World w : Bukkit.getWorlds()) {
				w.setAutoSave(true);
			}
			// restore tempignore
			for (File f : tempIgnore) {
				dBackup.getPlugin().getIgnoredFiles().remove(f);
			}
		}
		dBackup.getPlugin().getLogger().info("Backup complete!");
		backingUp = false;
		dBackup.getPlugin().setPlayerJoined(false);
		dBackup.getPlugin().getLoadedWorlds().clear();
	}

	// recursively compress files and directories
	private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
		if (fileToZip.getName().equals("session.lock")) {
			return;
		}
		for (File f : dBackup.getPlugin().ignoredFiles) { // return if it is ignored file
			if (f.getCanonicalPath().equals(fileToZip.getCanonicalPath())) {
				return;
			}
		}

		// fix windows archivers not being able to see files because they don't support
		// / (root) for zip files
		if (fileName.startsWith("/") || fileName.startsWith("\\")) {
			fileName = fileName.substring(1);
		}
		// make sure there won't be a "." folder
		if (fileName.startsWith("./") || fileName.startsWith(".\\")) {
			fileName = fileName.substring(2);
		}
		// truncate \. on windows (from the end of folder names)
		if (fileName.endsWith("/.") || fileName.endsWith("\\.")) {
			fileName = fileName.substring(0, fileName.length() - 2);
		}

		if (fileToZip.isDirectory()) { // if it's a directory, recursively search
			if (fileName.endsWith("/")) {
				zipOut.putNextEntry(new ZipEntry(fileName));
			} else {
				zipOut.putNextEntry(new ZipEntry(fileName + "/"));
			}
			zipOut.closeEntry();
			File[] children = fileToZip.listFiles();
			for (File childFile : children) {
				zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
			}
		} else { // if it's a file, store
			try {
				FileInputStream fis = new FileInputStream(fileToZip);
				ZipEntry zipEntry = new ZipEntry(fileName);
				zipOut.putNextEntry(zipEntry);
				byte[] bytes = new byte[1024];
				int length;
				while ((length = fis.read(bytes)) >= 0) {
					zipOut.write(bytes, 0, length);
				}
				fis.close();
			} catch (IOException e) {
				dBackup.getPlugin().getLogger().warning("Ignoring file '" + fileName + "'; " + e.getMessage());
			}
		}
	}
}
