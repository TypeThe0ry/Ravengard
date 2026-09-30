package net.swofty.service.orchestrator.endpoints;

import net.swofty.commons.protocol.RedisProtocol;
import net.swofty.commons.protocol.objects.game.GameInformationPushProtocol;
import net.swofty.commons.protocol.objects.orchestrator.ChooseGameProtocol;
import net.swofty.commons.redis.RedisMessageHandler;
import net.swofty.commons.redis.RedisClient;
import net.swofty.commons.redis.RedisMessageContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class GameChooseEndpoint implements RedisMessageHandler
		<ChooseGameProtocol.ChooseGameMessage,
				ChooseGameProtocol.ChooseGameResponse> {

	@Override
	public RedisProtocol<ChooseGameProtocol.ChooseGameMessage, ChooseGameProtocol.ChooseGameResponse> protocol() {
		return new ChooseGameProtocol();
	}

	@Override
	public ChooseGameProtocol.ChooseGameResponse handle(ChooseGameProtocol.ChooseGameMessage body, RedisMessageContext context) {
		try {
			CompletableFuture<GameInformationPushProtocol.Response> responseFuture = RedisClient.requestServerFromService(
					body.server().uuid(),
					new GameInformationPushProtocol(),
					new GameInformationPushProtocol.Request(body.player(), body.gameId()),
					60,
					TimeUnit.SECONDS);
			GameInformationPushProtocol.Response response = responseFuture.get(60, TimeUnit.SECONDS);
			if (response != null) {
				return new ChooseGameProtocol.ChooseGameResponse(true, null);
			}
			return new ChooseGameProtocol.ChooseGameResponse(false, "Dungeon server returned no response");
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return new ChooseGameProtocol.ChooseGameResponse(false, "Interrupted while choosing game");
		} catch (Exception exception) {
			String message = exception.getMessage();
			return new ChooseGameProtocol.ChooseGameResponse(false,
					message == null || message.isBlank() ? exception.getClass().getSimpleName() : message);
		}
	}

}
