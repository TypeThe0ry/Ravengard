package net.swofty.type.generic.command.commands.replay;

import net.minestom.server.command.builder.arguments.ArgumentType;
import net.swofty.commons.ServerType;
import net.swofty.commons.ServiceType;
import net.swofty.commons.protocol.objects.replay.ChooseReplayProtocolObject;
import net.swofty.proxyapi.ProxyService;
import net.swofty.type.generic.command.CommandParameters;
import net.swofty.type.generic.command.HypixelCommand;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.generic.user.categories.Rank;

import java.util.UUID;

@CommandParameters(labels = "replay", 
	description = "Watch a specific replay by ID",
	usage = "/replay <UUID> [分享码]",
	permission = Rank.DEFAULT,
	allowsConsole = false
)
public class ReplayCommand extends HypixelCommand {

	@Override
	public void registerUsage(MinestomCommand command) {
		var replayIdArg = ArgumentType.String("replayId");
		var hexArg = ArgumentType.String("hex");

		command.setDefaultExecutor((sender, _) ->
			sender.sendMessage("<c>用法：/replay \\<UUID> [分享码]"));

		command.addSyntax((sender, context) -> {
			HypixelPlayer player = (HypixelPlayer) sender;
			String replayIdStr = context.get(replayIdArg);

			UUID replayId = parseUuid(player, replayIdStr);
			if (replayId == null) return;

			sendToReplayViewer(player, replayId, null);
		}, replayIdArg);

		command.addSyntax((sender, context) -> {
			HypixelPlayer player = (HypixelPlayer) sender;
			String replayIdStr = context.get(replayIdArg);
			String hex = context.get(hexArg);

			UUID replayId = parseUuid(player, replayIdStr);
			if (replayId == null) return;

			String cleanHex = hex.startsWith("#") ? hex : "#" + hex;
			if (cleanHex.length() != 9) {
				player.sendMessage("<c>分享码格式无效。");
				return;
			}

			sendToReplayViewer(player, replayId, cleanHex);
		}, replayIdArg, hexArg);
	}

	private UUID parseUuid(HypixelPlayer player, String uuidStr) {
		try {
			return UUID.fromString(uuidStr);
		} catch (IllegalArgumentException e) {
			player.sendMessage("<c>回放 ID 格式无效，必须是有效的 UUID。");
			return null;
		}
	}

	private void sendToReplayViewer(HypixelPlayer player, UUID replayId, String shareCode) {
		player.sendMessage("<a>正在加载回放……");

		ProxyService replayService = new ProxyService(ServiceType.REPLAY);
		var request = new ChooseReplayProtocolObject.ChooseReplayMessage(player.getUuid(), replayId.toString(), shareCode);
		replayService.<ChooseReplayProtocolObject.ChooseReplayMessage, ChooseReplayProtocolObject.ChooseReplayResponse>handleRequest(request).thenAccept(response -> {
			if (!response.error()) {
				player.sendMessage("<7>正在将你传送到回放查看器……");
				player.sendTo(ServerType.REPLAY_VIEWER);
			} else {
				player.sendMessage("<c>未找到回放或加载失败。");
			}
		}).exceptionally(e -> {
			player.sendMessage("<c>加载回放失败：{}", e.getMessage());
			return null;
		});
	}
}
