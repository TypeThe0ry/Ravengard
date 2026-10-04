package net.swofty.type.ravengarddungeon.redis;

import net.swofty.commons.protocol.RedisProtocol;
import net.swofty.commons.protocol.objects.game.InstantiateGamePushProtocol;
import net.swofty.commons.protocol.objects.game.InstantiateGamePushProtocol.Request;
import net.swofty.commons.protocol.objects.game.InstantiateGamePushProtocol.Response;
import net.swofty.commons.redis.RedisMessageContext;
import net.swofty.commons.redis.RedisMessageHandler;
import net.swofty.type.ravengarddungeon.game.DungeonInstanceRegistry;

public class DungeonInstantiateGameHandler implements RedisMessageHandler<Request, Response> {

    private static final InstantiateGamePushProtocol PROTOCOL = new InstantiateGamePushProtocol();

    @Override
    public RedisProtocol<Request, Response> protocol() {
        return PROTOCOL;
    }

    @Override
    public Response handle(Request request, RedisMessageContext context) {
        try {
            DungeonInstanceRegistry.DungeonInstance instance;
            synchronized (DungeonInstanceRegistry.class) {
                if (DungeonInstanceRegistry.remainingSlots() <= 0) {
                    return Response.failure("所有地牢实例均繁忙");
                }
                instance = DungeonInstanceRegistry.create(request.gameType());
            }
            return Response.success(instance.getGameId().toString(), "generated", instance.getMode());
        } catch (Exception exception) {
            return Response.failure("创建地牢失败：" + exception.getMessage());
        }
    }
}
