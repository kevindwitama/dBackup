package xyz.dmblc.dbackup.commands;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import xyz.dmblc.dbackup.dBackup;
import xyz.dmblc.dbackup.util.BackupUtil;

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

public class BackupCommand implements TabExecutor {

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0 || args[0].equals("help")) {
			sender.sendMessage(
					"===== dBackup v" + dBackup.getPlugin().getDescription().getVersion() + " by dmblc =====");
			sender.sendMessage("Command aliases: dbackup, dbu");
			sender.sendMessage("> /dbackup everything - Starts a full backup of the server.");
			sender.sendMessage("> /dbackup help - Show a list of commands.");
			sender.sendMessage("> /dbackup reload - Reloads the plugin settings from the config.");
			sender.sendMessage("> /dbackup start - Starts a backup of the server.");
			return true;
		}

		switch (args[0]) {
		case "start":
			if (sender instanceof Player) {
				sender.sendMessage("Starting backup...");
				dBackup.getPlugin().getLogger().info("Player " + sender.getName() + " started a backup!");
			}
			Bukkit.getScheduler().runTaskAsynchronously(dBackup.getPlugin(), () -> {
				BackupUtil.doBackup(false);
			});
			if (sender instanceof Player) {
				sender.sendMessage("Backup complete!");
			}
			break;
		case "everything":
			if (sender instanceof Player) {
				sender.sendMessage("Starting full backup...");
				dBackup.getPlugin().getLogger().info("Player " + sender.getName() + " started a full backup!");
			}
			Bukkit.getScheduler().runTaskAsynchronously(dBackup.getPlugin(), () -> {
				BackupUtil.doBackup(true);
			});
			if (sender instanceof Player) {
				sender.sendMessage("Backup complete!");
			}
			break;
		case "reload":
			sender.sendMessage("Starting plugin reload...");
			dBackup.getPlugin().loadPlugin();
			sender.sendMessage("Reloaded dBackup!");
			break;
		default:
			sender.sendMessage("Do /dbackup help for a list of commands!");
			break;
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (!sender.hasPermission("dbackup.admin")) {
			return null;
		}
		if (args.length != 1) {
			return null;
		} else if (args[0].length() > 0) {
			switch (args[0].charAt(0)) {
			case 'e':
				return Arrays.asList("everything");
			case 'h':
				return Arrays.asList("help");
			case 'r':
				return Arrays.asList("reload");
			case 's':
				return Arrays.asList("start");
			default:
				return Arrays.asList("everything", "help", "reload", "start");
			}
		}
		return Arrays.asList("everything", "help", "reload", "start");
	}
}
