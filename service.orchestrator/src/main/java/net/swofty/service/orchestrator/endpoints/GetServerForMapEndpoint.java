package net.swofty.service.orchestrator.endpoints;

import net.swofty.commons.ServerType;
import net.swofty.commons.UnderstandableProxyServer;
import net.swofty.commons.protocol.RedisProtocol;
import net.swofty.commons.protocol.objects.orchestrator.GetServerForMapProtocol;
import net.swofty.commons.protocol.objects.game.InstantiateGamePushProtocol;
import net.swofty.commons.redis.RedisClient;
import net.swofty.commons.redis.RedisMessageHandler;
import net.swofty.service.orchestrator.OrchestratorCache;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.swofty.commons.redis.RedisMessageContext;

public class GetServerForMapEndpoint implements RedisMessageHandler
		<GetServerForMapProtocol.GetServerForMapMessage,
				GetServerForMapProtocol.GetServerForMapResponse> {

	@Override
	public RedisProtocol<GetServerForMapProtocol.GetServerForMapMessage, GetServerForMapProtocol.GetServerForMapResponse> protocol() {
		return new GetServerForMapProtocol();
	}

	@Override
	public GetServerForMapProtocol.GetServerForMapResponse handle(GetServerForMapProtocol.GetServerForMapMessage body, RedisMessageContext context) {
		return switch (body.type()) {
			case RAVENGARD_DUNGEON -> handleRavengardDungeon(body);
			default -> new GetServerForMapProtocol.GetServerForMapResponse(null, null, true, null);
		};
	}

	// a run holds twelve solo players or three teams of three; requests are serialised so two
	// players who queue in the same second share one instance instead of each creating their own
	private static int ravengardMaxPlayers(String mode) {
		return mode.equals("SOLO") ? 12 : 9;
	}
	private static final Object RAVENGARD_ALLOCATION = new Object();

	private GetServerForMapProtocol.GetServerForMapResponse handleRavengardDungeon(
			GetServerForMapProtocol.GetServerForMapMessage body) {
		synchronized (RAVENGARD_ALLOCATION) {
			return allocateRavengardDungeon(body);
		}
	}

	private GetServerForMapProtocol.GetServerForMapResponse allocateRavengardDungeon(
			GetServerForMapProtocol.GetServerForMapMessage body) {
		try {
			String requested = body.mode() == null || body.mode().isEmpty() ? "TRIOS" : body.mode();
			String mode = requested.equals("STANDARD") ? "TRIOS" : requested;
			boolean matchmade = mode.equals("SOLO") || mode.equals("TRIOS");
			int neededSlots = body.neededSlots() > 0 ? body.neededSlots() : 1;

			// queue joins share an open run of the same mode when one exists
			if (matchmade) {
				OrchestratorCache.GameWithServer existing = OrchestratorCache.findExisting(
						ServerType.RAVENGARD_DUNGEON, ravengardMaxPlayers(mode), null, neededSlots, mode);
				if (existing != null) {
					OrchestratorCache.GameServerState hosting = OrchestratorCache.getServerByUuid(existing.serverUuid());
					if (hosting != null) {
						return new GetServerForMapProtocol.GetServerForMapResponse(
								toProxyServer(hosting), existing.game().getGameId().toString(), true, null);
					}
				}
			}

			OrchestratorCache.GameServerState server = OrchestratorCache.pickServerForNewGame(ServerType.RAVENGARD_DUNGEON);
			Logger.info("[matchmaking] RAVENGARD_DUNGEON mode='{}' -> server={}",
					mode, server == null ? "NONE" : server.shortName());
			if (server == null) {
				return new GetServerForMapProtocol.GetServerForMapResponse(null, null, true,
						OrchestratorCache.hasServer(ServerType.RAVENGARD_DUNGEON)
								? "All dungeon instances are busy, please try again shortly"
								: "No dungeon servers online");
			}

			CompletableFuture<InstantiateGamePushProtocol.Response> responseFuture = RedisClient.requestServerFromService(
					server.uuid(),
					new InstantiateGamePushProtocol(),
					new InstantiateGamePushProtocol.Request(mode, body.map()),
					60,
					java.util.concurrent.TimeUnit.SECONDS);
			InstantiateGamePushProtocol.Response response = responseFuture.get(60, java.util.concurrent.TimeUnit.SECONDS);
			if (response != null && response.success()) {
				OrchestratorCache.registerNewGame(server, java.util.UUID.fromString(response.gameId()),
						ServerType.RAVENGARD_DUNGEON, matchmade ? "generated" : body.map(), mode,
						matchmade ? neededSlots : 0);
				return new GetServerForMapProtocol.GetServerForMapResponse(
						toProxyServer(server), response.gameId(), true, null);
			}
			return new GetServerForMapProtocol.GetServerForMapResponse(null, null, true,
					response == null ? "No response from dungeon server" : response.error());
		} catch (Exception e) {
			Logger.error(e, "Failed to allocate Ravengard dungeon");
			return new GetServerForMapProtocol.GetServerForMapResponse(null, null, true, e.getMessage());
		}
	}

	private static UnderstandableProxyServer toProxyServer(OrchestratorCache.GameServerState server) {
		return new UnderstandableProxyServer(
				server.shortName(),
				server.uuid(),
				server.type(),
				-1,
				new ArrayList<>(),
				server.maxPlayers(),
				server.shortName());
	}

}
