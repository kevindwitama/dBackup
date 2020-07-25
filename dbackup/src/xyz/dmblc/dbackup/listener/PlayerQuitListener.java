package xyz.dmblc.dbackup.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

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

public class PlayerQuitListener implements Listener {

	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		if (dBackup.getPlugin().isDebugMode()) {
			dBackup.getPlugin().getLogger().info("Player " + event.getPlayer().getName() + " quit from world "
					+ event.getPlayer().getWorld().getName() + "!");
		}
		dBackup.getPlugin().setPlayerJoined(true);
		if (!dBackup.getPlugin().getLoadedWorlds().contains(event.getPlayer().getWorld())) {
			dBackup.getPlugin().getLoadedWorlds().add(event.getPlayer().getWorld());
		}
	}

}