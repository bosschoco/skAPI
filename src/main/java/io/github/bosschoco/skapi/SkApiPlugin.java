package io.github.bosschoco.skapi;

import ch.njol.skript.Skript;
import io.github.bosschoco.skapi.http.HttpService;
import io.github.bosschoco.skapi.skript.SkApiModule;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;

/**
 * skAPI 메인 클래스.
 * Skript 에 문법(syntax)을 등록하고, HTTP 클라이언트의 생명주기를 관리한다.
 */
public final class SkApiPlugin extends JavaPlugin {

	private static @Nullable SkApiPlugin instance;

	private @Nullable HttpService http;
	private @Nullable SkApiSettings settings;

	@Override
	public void onEnable() {
		instance = this;
		saveDefaultConfig();
		reloadSettings();

		if (!Skript.isAcceptRegistrations()) {
			getLogger().severe("Skript 가 이미 문법 등록을 마감했습니다. 서버를 재시작해주세요. (플러그인 리로드는 지원하지 않습니다)");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		SkriptAddon addon = Skript.instance().registerAddon(SkApiPlugin.class, "skAPI");
		addon.loadModules(new SkApiModule());

		PluginCommand command = getCommand("skapi");
		if (command != null) {
			SkApiCommand executor = new SkApiCommand(this);
			command.setExecutor(executor);
			command.setTabCompleter(executor);
		}

		getLogger().info("skAPI " + getPluginMeta().getVersion() + " 활성화 완료 (Skript " + Skript.getVersion() + ")");
	}

	@Override
	public void onDisable() {
		if (http != null) {
			http.shutdown();
			http = null;
		}
		instance = null;
	}

	/** config.yml 을 다시 읽고 HTTP 클라이언트를 새 설정으로 교체한다. */
	public void reloadSettings() {
		reloadConfig();
		SkApiSettings loaded = SkApiSettings.from(getConfig());
		settings = loaded;
		if (http == null) {
			http = new HttpService(getLogger(), loaded);
		} else {
			http.configure(loaded);
		}
	}

	public static SkApiPlugin get() {
		SkApiPlugin plugin = instance;
		if (plugin == null)
			throw new IllegalStateException("skAPI 가 활성화되지 않았습니다.");
		return plugin;
	}

	public static HttpService http() {
		HttpService service = get().http;
		if (service == null)
			throw new IllegalStateException("skAPI HTTP 서비스가 준비되지 않았습니다.");
		return service;
	}

	public static SkApiSettings settings() {
		SkApiSettings current = get().settings;
		if (current == null)
			throw new IllegalStateException("skAPI 설정이 로드되지 않았습니다.");
		return current;
	}

	/**
	 * 작업을 다음 틱에 메인 스레드에서 실행한다.
	 * 이미 메인 스레드라도 항상 다음 틱으로 미뤄서, 현재 실행 중인 트리거와 섞이지 않게 한다.
	 * 플러그인이 꺼지는 중이면 조용히 무시한다.
	 */
	public static void runOnMainThread(Runnable task) {
		SkApiPlugin plugin = instance;
		if (plugin == null || !plugin.isEnabled())
			return;
		try {
			Bukkit.getScheduler().runTask(plugin, task);
		} catch (IllegalPluginAccessException ignored) {
			// 서버 종료 중
		}
	}
}
