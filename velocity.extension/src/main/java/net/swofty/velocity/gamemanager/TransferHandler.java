package net.swofty.velocity.gamemanager;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.swofty.commons.ServerType;
import net.swofty.commons.protocol.objects.proxy.from.GivePlayersOriginTypeProtocol;
import net.swofty.commons.protocol.objects.proxy.from.PlayerSwitchedProtocol;
import net.swofty.commons.text.Text;
import net.swofty.velocity.text.ProxyText;
import net.swofty.commons.redis.RedisClient;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public record TransferHandler(Player player) {
	public static final Map<Player, ServerType> playersGoalServerType = new ConcurrentHashMap<>();
	private static final Map<Player, RegisteredServer> playersOriginServer = new ConcurrentHashMap<>();
	private static final Set<Player> disregard = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, ServerType> afkOriginLobbyType = new ConcurrentHashMap<>();
	private static final Set<UUID> afkReturnInProgress = ConcurrentHashMap.newKeySet();

	public boolean isInLimbo() {
		return playersGoalServerType.containsKey(player);
	}

	public boolean isInAfkLimbo() {
		return afkOriginLobbyType.containsKey(player.getUniqueId());
	}

	public static void clearPlayerState(UUID playerUuid) {
		afkOriginLobbyType.remove(playerUuid);
		afkReturnInProgress.remove(playerUuid);
		clearCrashRecovery(playerUuid);
	}

	public static boolean beginCrashRecovery(UUID playerUuid) {
		return CrashRecoveryGate.begin(playerUuid);
	}

	public static void clearCrashRecovery(UUID playerUuid) {
		CrashRecoveryGate.clear(playerUuid);
	}

	public static void clearCrashRecoveryOnServerConnected(UUID playerUuid, RegisteredServer server) {
		clearCrashRecovery(playerUuid);
	}

	public void addToDisregard() {
		disregard.add(player);
	}

	public void removeFromDisregard() {
		disregard.remove(player);
	}

	public CompletableFuture<Boolean> sendToLimbo() {
		return sendToRavengardLobby();
	}

	public CompletableFuture<Boolean> sendToLimboFromAfk(ServerType originType) {
		return sendToRavengardLobby();
	}

	public void returnFromAfkLimbo() {
		Thread.startVirtualThread(() -> {
			UUID uuid = player.getUniqueId();
			if (!afkReturnInProgress.add(uuid)) {
				return;
			}

			try {
				if (!isInAfkLimbo()) {
					return;
				}

				GameManager.GameServer destination = BalanceConfigurations.getServerFor(player, ServerType.RAVENGARD_LOBBY);

				if (destination == null) {
					player.sendMessage(Text.of("<c>There are no lobby servers available right now."));
					return;
				}

				afkOriginLobbyType.remove(uuid);
				player.sendMessage(Text.of("<7>Sending to server {}...", destination.displayName()));
				player.createConnectionRequest(destination.registeredServer()).connectWithIndication();
			} finally {
				afkReturnInProgress.remove(uuid);
			}
		});
	}

	private CompletableFuture<Boolean> sendToRavengardLobby() {
		CompletableFuture<Boolean> future = new CompletableFuture<>();
		Thread.startVirtualThread(() -> {
			try {
				GameManager.GameServer destination = BalanceConfigurations.getServerFor(player, ServerType.RAVENGARD_LOBBY);
				if (destination == null) {
					player.sendMessage(Text.of("<c>There are no Ravengard Lobby servers available right now."));
					future.complete(false);
					return;
				}
				if (player.getCurrentServer()
					.map(connection -> connection.getServer().equals(destination.registeredServer()))
					.orElse(false)) {
					future.complete(true);
					return;
				}
				player.createConnectionRequest(destination.registeredServer()).connectWithIndication()
					.whenComplete((connected, error) -> {
						if (error != null) {
							future.completeExceptionally(error);
						} else {
							future.complete(connected);
						}
					});
			} catch (Exception exception) {
				future.completeExceptionally(exception);
			}
		});
		return future;
	}

	public void previousServerIsFinished(RegisteredServer manualPick) {
		Thread.startVirtualThread(() -> {
			if (disregard.contains(player)) return;

			RegisteredServer originServer = playersOriginServer.get(player);
			ServerType originServerType = GameManager.getTypeFromRegisteredServer(originServer);

			UUID serverUUID = UUID.fromString(manualPick.getServerInfo().getName());
			UUID originServerUUID = UUID.fromString(originServer.getServerInfo().getName());

			RedisClient.requestServer(serverUUID,
					new GivePlayersOriginTypeProtocol(),
					new GivePlayersOriginTypeProtocol.Request(
							player.getUniqueId().toString(), originServerType.name()));

			playersGoalServerType.remove(player);
			playersOriginServer.remove(player);

			GameManager.GameServer manualPickAsGame = GameManager.getFromUUID(serverUUID);
			player.sendMessage(Text.of("<7>Sending to server {}...", manualPickAsGame.displayName()));
			player.createConnectionRequest(manualPick).connectWithIndication();

			RedisClient.requestServer(originServerUUID,
					new PlayerSwitchedProtocol(),
					new PlayerSwitchedProtocol.Request(player.getUniqueId().toString()));
		});
	}

	public void previousServerIsFinished() {
		Thread.startVirtualThread(() -> {
			if (disregard.contains(player) || !isInLimbo()) return;

			ServerType type = playersGoalServerType.get(player);
			if (type == null) {
				playersGoalServerType.remove(player);
				playersOriginServer.remove(player);
				return;
			}
			GameManager.GameServer server = BalanceConfigurations.getServerFor(player, type);

			if (server == null) {
				playersGoalServerType.remove(player);
				playersOriginServer.remove(player);
				ProxyText.disconnect(player, "<c>There are no Hypixel (type={}) servers available at the moment.", type.name());
				return;
			}

			RegisteredServer originServer = playersOriginServer.get(player);
			if (originServer == null) {
				playersGoalServerType.remove(player);
				return;
			}
			UUID originServerUUID = UUID.fromString(originServer.getServerInfo().getName());
			UUID sendingToServerUUID = server.internalID();
			ServerType originServerType = GameManager.getTypeFromRegisteredServer(originServer);

			RedisClient.requestServer(sendingToServerUUID,
					new GivePlayersOriginTypeProtocol(),
					new GivePlayersOriginTypeProtocol.Request(
							player.getUniqueId().toString(), originServerType.name()));

			playersOriginServer.remove(player);
			playersGoalServerType.remove(player);

			player.sendMessage(Text.of("<7>Sending to server {}...", server.displayName()));
			player.createConnectionRequest(server.registeredServer()).connectWithIndication();

			RedisClient.requestServer(originServerUUID,
					new PlayerSwitchedProtocol(),
					new PlayerSwitchedProtocol.Request(player.getUniqueId().toString()));
		});
	}

	public void transferTo(ServerType type) {
		if (type == null) {
			return;
		}

		GameManager.GameServer server = BalanceConfigurations.getServerFor(player, type);
		if (server == null) {
			player.sendMessage(Text.of("<c>There are no Hypixel (type={}) servers available at the moment.", type.name()));
			return;
		}

		player.sendMessage(Text.of("<7>Sending to server {}...", server.displayName()));
		transferTo(server.registeredServer());
	}

	public void forceRemoveFromLimbo() {
		playersGoalServerType.remove(player);
		playersOriginServer.remove(player);
		clearPlayerState(player.getUniqueId());
	}

	public CompletableFuture<Void> transferTo(RegisteredServer toTransferTo) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		Thread.startVirtualThread(() -> {
			try {
				RegisteredServer originServer = playersOriginServer.get(player);
				if (originServer == null) {
					player.getCurrentServer().ifPresent(conn -> playersOriginServer.put(player, conn.getServer()));
					originServer = playersOriginServer.get(player);
				}
				ServerType originServerType = GameManager.getTypeFromRegisteredServer(originServer);

				playersGoalServerType.remove(player);
				playersOriginServer.remove(player);

				UUID serverUUID = UUID.fromString(toTransferTo.getServerInfo().getName());

				if (originServer != null && originServerType != null) {
					RedisClient.requestServer(serverUUID,
							new GivePlayersOriginTypeProtocol(),
							new GivePlayersOriginTypeProtocol.Request(
									player.getUniqueId().toString(), originServerType.name()));
				}

				player.createConnectionRequest(toTransferTo).connectWithIndication();
				future.complete(null);
			} catch (Exception e) {
				future.completeExceptionally(e);
			}
		});
		return future;
	}

}
