package io.github.bosschoco.skapi;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;

/** /skapi reload, /skapi info */
final class SkApiCommand implements TabExecutor {

	private final SkApiPlugin plugin;

	SkApiCommand(SkApiPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		String sub = args.length == 0 ? "info" : args[0].toLowerCase();
		switch (sub) {
			case "reload" -> {
				plugin.reloadSettings();
				sender.sendMessage("§a[skAPI] config.yml 을 다시 불러왔습니다.");
			}
			case "info" -> {
				SkApiSettings s = SkApiPlugin.settings();
				sender.sendMessage("§e[skAPI] §f버전 " + plugin.getPluginMeta().getVersion());
				sender.sendMessage("§7 - 기본 타임아웃: " + s.defaultTimeout().toMillis() / 1000.0 + "초");
				sender.sendMessage("§7 - 연결 타임아웃: " + s.connectTimeout().toMillis() / 1000.0 + "초");
				sender.sendMessage("§7 - 메인 스레드 동기 요청 상한: " + s.maxMainThreadTimeout().toMillis() / 1000.0 + "초");
				sender.sendMessage("§7 - 디버그: " + (s.debug() ? "켜짐" : "꺼짐"));
			}
			default -> sender.sendMessage("§c사용법: /" + label + " <reload|info>");
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 1)
			return List.of("reload", "info").stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
		return List.of();
	}
}
