package xyz.dmblc.dbackup;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import xyz.dmblc.dbackup.commands.BackupCommand;
import xyz.dmblc.dbackup.listener.PlayerChangedWorldListener;
import xyz.dmblc.dbackup.listener.PlayerQuitListener;
import xyz.dmblc.dbackup.util.BackupUtil;
import xyz.dmblc.dbackup.util.CronUtil;

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

public class dBackup extends JavaPlugin {

	// config options
	String crontask, backupFormat;
	File backupPath;
	int maxBackups;
	int maxFullBackups;

	boolean backupPluginJars, backupPluginConfs;

	List<String> filesToIgnore;
	public List<File> ignoredFiles = new ArrayList<>();

	boolean debugMode;

	BukkitTask bukkitCronTask = null;

	boolean playerJoined = true;
	static List<World> loadedWorlds;
	static List<ChunkSnapshot> loadedChunks;

	PlayerQuitListener playerQuitListener = new PlayerQuitListener();
	PlayerChangedWorldListener changeWorldListener = new PlayerChangedWorldListener();

	BukkitRunnable initFullBackup;

	// called on reload and when the plugin first loads
	public void loadPlugin() {
		getIgnoredFiles().clear();

		saveDefaultConfig();
		getConfig().options().copyDefaults(true);

		if (!getDataFolder().exists())
			getDataFolder().mkdir();

		try {
			getConfig().load(getDataFolder() + "/config.yml");
		} catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
		}

		// load config data
		crontask = getConfig().getString("crontask");
		backupFormat = getConfig().getString("backup-format");
		backupPath = new File(getConfig().getString("backup-path"));
		maxBackups = getConfig().getInt("max-backups");
		maxFullBackups = getConfig().getInt("max-full-backups");
		backupPluginJars = getConfig().getBoolean("backup.pluginjars");
		backupPluginConfs = getConfig().getBoolean("backup.pluginconfs");
		filesToIgnore = getConfig().getStringList("backup.ignore");
		for (String s : filesToIgnore) {
			getIgnoredFiles().add(new File(s));
		}
		debugMode = getConfig().getBoolean("debug");

		// make sure backup location exists
		if (!getBackupPath().exists())
			getBackupPath().mkdir();

		// stop cron task if it is running
		if (bukkitCronTask != null)
			bukkitCronTask.cancel();

		// start full backup on initial server load
		initFullBackup = new BukkitRunnable() {
			@Override
			public void run() {
				SortedMap<Long, File> mapBackups = new TreeMap<>(); // oldest files to newest

				for (File f : getBackupPath().listFiles()) {
					if (f.getName().endsWith(".zip") && f.getName().startsWith("FULL-")) {
						mapBackups.put(f.lastModified(), f);
					}
				}

				if (!mapBackups.isEmpty() && (new Date().getTime() - mapBackups.lastKey()) <= 216000) {
					getLogger().info("Last full backup is less than an hour old, skipping.");
					return;
				} else {
					getLogger().info("Starting full backup for current server session...");
					BackupUtil.doBackup(true);
				}
			}
		};
		initFullBackup.runTaskLaterAsynchronously(this, 20);

		// start cron task
		CronUtil.checkCron();
		bukkitCronTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
			if (CronUtil.run()) {
				if (Bukkit.getServer().getOnlinePlayers().size() > 0 || isPlayerJoined()) {
					BackupUtil.doBackup(false);
				} else {
					getLogger().info("Skipping backup, no one was online.");
				}
			}
		}, 20, 20);
	}

	@Override
	public void onEnable() {
		getLogger().info("Initializing dBackup by dmblc");

		getServer().getPluginManager().registerEvents(playerQuitListener, this);
		getServer().getPluginManager().registerEvents(changeWorldListener, this);

		getCommand("dbackup").setExecutor(new BackupCommand());

		loadPlugin();

		loadedWorlds = Bukkit.getWorlds();

		getLogger().info("Plugin initialized!");
	}

	@Override
	public void onDisable() {
		getLogger().info("Disabled dBackup!");
	}

	public static dBackup getPlugin() {
		return (dBackup) Bukkit.getPluginManager().getPlugin("dBackup");
	}

	public List<World> getLoadedWorlds() {
		return loadedWorlds;
	}
	
	public static List<ChunkSnapshot> getLoadedChunks() {
		return loadedChunks;
	}

	public String getCrontask() {
		return crontask;
	}

	public int getMaxBackups() {
		return maxBackups;
	}

	public File getBackupPath() {
		return backupPath;
	}

	public int getMaxFullBackups() {
		return maxFullBackups;
	}

	public boolean isBackupPluginJars() {
		return backupPluginJars;
	}

	public boolean isBackupPluginConfs() {
		return backupPluginConfs;
	}

	public List<File> getIgnoredFiles() {
		return ignoredFiles;
	}

	public String getBackupFormat() {
		return backupFormat;
	}

	public boolean isDebugMode() {
		return debugMode;
	}

	public boolean isPlayerJoined() {
		return playerJoined;
	}

	public void setPlayerJoined(boolean playerJoined) {
		this.playerJoined = playerJoined;
	}

}
