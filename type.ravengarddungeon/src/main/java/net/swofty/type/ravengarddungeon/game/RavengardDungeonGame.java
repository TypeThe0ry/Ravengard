package net.swofty.type.ravengarddungeon.game;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.WorldBorder;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.swofty.commons.ServerType;
import net.swofty.type.game.game.AbstractGame;
import net.swofty.type.game.game.CountdownConfig;
import net.swofty.type.game.game.Game;
import net.swofty.type.game.game.GameState;
import net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig;
import net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer;
import net.swofty.type.ravengardgeneric.classes.RavengardClass;
import net.swofty.type.ravengardgeneric.classes.RavengardSelection;
import net.swofty.type.ravengardgeneric.hud.RavengardDeathWallView;
import net.swofty.type.ravengardgeneric.hud.RavengardHud;
import net.swofty.type.ravengardgeneric.profile.RavengardProfiles;
import net.swofty.type.ravengardgeneric.profile.RavengardRunState;
import net.swofty.type.ravengardgeneric.sound.RavengardSounds;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class RavengardDungeonGame extends AbstractGame<RavengardDungeonPlayer> {
    private static final int TEAM_SIZE = 3;
    public static final List<Pos> SPAWN_POINTS = List.of(
            new Pos(-188.5, 66, -134.5),
            new Pos(-188.5, 66, -81.5),
            new Pos(-187.5, 66, -26.5)
    );
    private final RavengardDungeonConfig config;
    private final List<Pos> spawnPoints;
    private final List<Pos> extractionPoints;
    private final long deathWallSeed;
    private long startedAtNanos;
    static final int READY_GRACE_TICKS = 200;
    private int readyWaitTicks;
    private Task gameTask;
    private Task ambienceTask;
    private double wallInitialRadius = 256.0;
    private RavengardDeathWall.Timeline deathWallTimeline =
            RavengardDeathWall.centeredTimeline(0, 0, wallInitialRadius);
    private long lastDeathWallDamageAt;
    private long lastHudSecond = -1;
    private RavengardDeathWall.Phase lastWallPhase;
    private int worldBorderStage;
    private RavengardDeathWall.Phase worldBorderPhase;
    private long lastWorldBorderSecond = -1;
    private WorldBorder syncedWorldBorder = WorldBorder.DEFAULT_BORDER;
    private volatile RavengardDeathWall.State deathWallState;
    private final BossBar deathWallProgress = BossBar.bossBar(Component.empty(), 0,
            BossBar.Color.RED, BossBar.Overlay.PROGRESS);

    public RavengardDungeonGame(Instance instance, RavengardDungeonConfig config) {
        this(instance, config, SPAWN_POINTS);
    }

    public RavengardDungeonGame(Instance instance, RavengardDungeonConfig config, List<Pos> spawnPoints) {
        this(instance, config, spawnPoints, List.of());
    }

    public RavengardDungeonGame(Instance instance, RavengardDungeonConfig config, List<Pos> spawnPoints,
                                List<Pos> extractionPoints) {
        super(instance, RavengardDungeonGame::onGameEvent);
        this.config = config;
        this.spawnPoints = List.copyOf(spawnPoints == null || spawnPoints.isEmpty() ? SPAWN_POINTS : spawnPoints);
        this.extractionPoints = List.copyOf(extractionPoints == null ? List.of() : extractionPoints);
        java.util.UUID gameUuid = java.util.UUID.fromString(getGameId());
        this.deathWallSeed = gameUuid.getMostSignificantBits() ^ gameUuid.getLeastSignificantBits();
    }

    private static void onGameEvent(Object event) {
        if (!(event instanceof net.swofty.type.game.game.event.CountdownTickEvent tick)
                || !tick.shouldAnnounce() || tick.remainingSeconds() <= 0
                || !(tick.getGame() instanceof RavengardDungeonGame game)) {
            return;
        }
        int seconds = tick.remainingSeconds();
        for (RavengardDungeonPlayer player : game.players.values()) {
            if (!isPlayReady(player)) continue;
            player.sendMessage("<7>The game starts in <e>{}<7> {}!", seconds,
                    seconds == 1 ? "second" : "seconds");
            RavengardSounds.play(player, RavengardSounds.Cue.MATCH_COUNTDOWN);
        }
    }

    public RavengardDungeonConfig getConfig() {
        return config;
    }

    public List<Pos> getExtractionPoints() {
        return extractionPoints;
    }

    public void configureDeathWall(double centerX, double centerZ, double initialRadius) {
        double mapRadius = Math.max(RavengardDeathWall.FINAL_RADIUS, initialRadius);
        deathWallTimeline = RavengardDeathWall.timeline(
                centerX, centerZ, mapRadius, extractionPoints, deathWallSeed);
        wallInitialRadius = deathWallTimeline.initialRadius();
        deathWallState = RavengardDeathWall.state(0, deathWallTimeline);
    }

    @Override
    public Game.JoinResult join(RavengardDungeonPlayer player) {
        if (!isPlayReady(player)) {
            return new Game.JoinResult.Denied("Player connection is not ready");
        }
        Game.JoinResult result = super.join(player);
        if (result instanceof Game.JoinResult.Success) {
            int playerIndex = players.size() - 1;
            player.setDungeonTeam(playerIndex / TEAM_SIZE);
            player.setDungeonDead(false);
            RavengardRunState.begin(player.getUuid(), getGameId());
            Pos spawn = spawnPoints.get(playerIndex % spawnPoints.size());
            if (player.getInstance() == instance) {
                player.teleport(spawn).whenComplete((ignored, error) -> {
                    if (error != null) {
                        org.tinylog.Logger.error(error, "Failed teleporting {} inside Ravengard dungeon {}",
                                player.getUsername(), getGameId());
                        return;
                    }
                    updateHud(player);
                    org.tinylog.Logger.info("Placed {} into Ravengard dungeon {} at {} without instance switch",
                            player.getUsername(), getGameId(), spawn);
                });
            } else {
                player.setInstance(instance, spawn).whenComplete((ignored, error) -> {
                    if (error != null) {
                        org.tinylog.Logger.error(error, "Failed moving {} into Ravengard dungeon {}",
                                player.getUsername(), getGameId());
                        return;
                    }
                    updateHud(player);
                    org.tinylog.Logger.info("Moved {} into Ravengard dungeon {} at {}",
                            player.getUsername(), getGameId(), spawn);
                });
            }
        }
        return result;
    }

    @Override
    public int getMaxPlayers() {
        return 21;
    }

    @Override
    public int getMinPlayers() {
        return 1;
    }

    public Pos getSpawnPoint(int index) {
        return spawnPoints.get(Math.floorMod(index, spawnPoints.size()));
    }

    public List<Pos> getSpawnPoints() {
        return spawnPoints;
    }

    @Override
    protected CountdownConfig getCountdownConfig() {
        return CountdownConfig.simple(30);
    }

    @Override
    public void start() {
        if (state != GameState.WAITING && state != GameState.COUNTDOWN) return;
        super.start();
        if (state != GameState.IN_PROGRESS) return;
        startedAtNanos = 0;
        readyWaitTicks = 0;
        lastDeathWallDamageAt = 0;
        lastHudSecond = -1;
        worldBorderStage = 0;
        worldBorderPhase = null;
        lastWorldBorderSecond = -1;
        deathWallState = RavengardDeathWall.state(0, deathWallTimeline);
        if (gameTask != null) gameTask.cancel();
        gameTask = MinecraftServer.getSchedulerManager().buildTask(this::tickGame)
                .repeat(TaskSchedule.tick(1))
                .schedule();
        tryBeginActiveRun();
    }

    public void eliminate(RavengardDungeonPlayer player) {
        eliminate(player, null);
    }

    public void eliminate(RavengardDungeonPlayer player, String killer) {
        if (state != GameState.IN_PROGRESS || player.isDungeonDead()) return;
        if (killer != null) {
            for (RavengardDungeonPlayer viewer : players.values()) {
                String victimColor = viewer.getDungeonTeam() == player.getDungeonTeam()
                        ? "<e>" : "<f>";
                viewer.sendMessage(victimColor + "{} <7>was defeated by <f>{}<7>!",
                        player.getUsername(), killer);
            }
        }
        player.setDungeonDead(true);
        stripToKit(player);
        updateHud(player);
        Optional<RavengardDungeonPlayer> teammate = aliveTeammate(player);
        if (teammate.isEmpty()) {
            routeTeamToLobby(player.getDungeonTeam());
            return;
        }
        lockTo(player, teammate.get());
        for (RavengardDungeonPlayer spectator : teammatesOf(player)) {
            if (spectator.isDungeonDead()) lockTo(spectator, teammate.get());
        }
    }

    public boolean canInteract(RavengardDungeonPlayer player) {
        return state == GameState.IN_PROGRESS && startedAtNanos != 0
                && players.containsKey(player.getUuid()) && !player.isDungeonDead()
                && RavengardRunState.isActive(player.getUuid(), getGameId());
    }

    public void forfeitRun(RavengardDungeonPlayer player) {
        if (!RavengardRunState.isActive(player.getUuid(), getGameId())) return;
        if (startedAtNanos != 0) stripToKit(player);
        RavengardRunState.finish(player.getUuid(), getGameId());
    }

    private static void stripToKit(RavengardDungeonPlayer player) {
        player.getInventory().clear();
        RavengardClass playerClass = player.getRavengardClass();
        if (playerClass != null) RavengardSelection.giveKit(player, playerClass);
    }

    public void disconnect(RavengardDungeonPlayer player) {
        RavengardRunState.abort(player.getUuid());
        if (state == GameState.IN_PROGRESS) {
            int team = player.getDungeonTeam();
            if (!removePlayerFromDungeon(player)) return;
            if (alivePlayers(team).isEmpty()) routeTeamToLobby(team);
            else retargetSpectators(team);
        } else {
            if (detachForTransfer(player.getUuid())) {
                DungeonInstanceRegistry.markPlayerLeft(player.getUuid(), this);
                if (ownsPlayerState(getGameId(), player.getGameId())) {
                    player.setGameId(null);
                    player.setSpectatingTeammate(null);
                }
            }
        }
    }

    public int getAlivePlayerCount() {
        return (int) players.values().stream().filter(player -> !player.isDungeonDead()).count();
    }

    public int getAliveTeamCount() {
        return (int) players.values().stream().filter(player -> !player.isDungeonDead())
                .map(RavengardDungeonPlayer::getDungeonTeam).distinct().count();
    }

    public long getElapsedSeconds() {
        return startedAtNanos == 0
                ? 0
                : TimeUnit.NANOSECONDS.toSeconds(Math.max(0, System.nanoTime() - startedAtNanos));
    }

    public long getRemainingSeconds() {
        return getDeathWallState().remainingSeconds();
    }

    public String getDeathWallStatus() {
        return switch (state) {
            case WAITING -> "Waiting for game start";
            case COUNTDOWN -> "Game starting soon";
            case IN_PROGRESS -> RavengardDeathWall.status(getDeathWallState());
            default -> "Dungeon ended";
        };
    }

    String getDeathWallStatus(RavengardDeathWall.State wall) {
        return state == GameState.IN_PROGRESS
                ? RavengardDeathWall.status(wall)
                : getDeathWallStatus();
    }

    RavengardDeathWall.State getDeathWallState() {
        RavengardDeathWall.State current = deathWallState;
        if (state == GameState.IN_PROGRESS && current != null) return current;
        return RavengardDeathWall.state(getElapsedSeconds(), deathWallTimeline);
    }

    private void tickGame() {
        if (state != GameState.IN_PROGRESS) {
            if (gameTask != null) gameTask.cancel();
            return;
        }
        if (startedAtNanos == 0 && !tryBeginActiveRun()) return;
        enforceSpectators();
        applyDeathWall();
        if (getRemainingSeconds() > 0) return;
        endExpiredMatch();
    }

    private boolean tryBeginActiveRun() {
        if (startedAtNanos != 0) return true;
        List<RavengardDungeonPlayer> unready = players.values().stream()
                .filter(player -> !isPlayReady(player) || player.getInstance() != instance)
                .toList();
        if (!unready.isEmpty()) {
            if (++readyWaitTicks <= READY_GRACE_TICKS) return false;
            for (RavengardDungeonPlayer player : unready) {
                org.tinylog.Logger.warn("Dropping {} from Ravengard dungeon {}: never finished loading in",
                        player.getUsername(), getGameId());
                forfeitRun(player);
                removePlayerFromDungeon(player);
            }
            if (players.isEmpty()) {
                end();
                return false;
            }
        }
        startedAtNanos = System.nanoTime();
        startDeathWall();
        for (RavengardDungeonPlayer player : players.values()) {
            player.setDungeonDead(false);
            player.setGameMode(GameMode.SURVIVAL);
            updateHud(player);
            player.hideBossBar(deathWallProgress);
            RavengardSounds.play(player, RavengardSounds.Cue.MATCH_START);
        }
        if (ambienceTask != null) ambienceTask.cancel();
        ambienceTask = MinecraftServer.getSchedulerManager().buildTask(() ->
                        players.values().stream().filter(RavengardDungeonGame::isPlayReady).forEach(player ->
                                RavengardSounds.play(player, RavengardSounds.Cue.DUNGEON_AMBIENCE)))
                .delay(TaskSchedule.seconds(11))
                .repeat(TaskSchedule.seconds(252))
                .schedule();
        return true;
    }

    static boolean isPlayReady(RavengardDungeonPlayer player) {
        return player.getInstance() != null
                && player.getPlayerConnection().getClientState() == ConnectionState.PLAY
                && player.getPlayerConnection().getServerState() == ConnectionState.PLAY;
    }

    private void endExpiredMatch() {
        List<RavengardDungeonPlayer> remainingPlayers = players.values().stream().toList();
        end();
        if (gameTask != null) gameTask.cancel();
        resetDeathWall();
        for (RavengardDungeonPlayer player : remainingPlayers) {
            if (!player.isDungeonDead()) {
                player.sendMessage("<c>The death wall consumed the dungeon. You failed to extract in time.");
            }
        }
        routePlayersToLobby(remainingPlayers);
    }

    private void applyDeathWall() {
        if (state != GameState.IN_PROGRESS) return;
        long elapsed = getElapsedSeconds();
        RavengardDeathWall.State wall = RavengardDeathWall.state(elapsed, deathWallTimeline);
        deathWallState = wall;
        if (elapsed != lastHudSecond) {
            lastHudSecond = elapsed;
            players.values().forEach(player -> updateHud(player, wall));
        }
        RavengardDeathWall.Phase phase = wall.phase();
        if (phase != lastWallPhase) {
            lastWallPhase = phase;
            org.tinylog.Logger.info("Ravengard wall phase: game={} phase={} stage={} elapsed={} current=({}, {}, {}) next=({}, {}, {}) schedule=official-behavior-reconstruction",
                    getGameId(), phase, wall.stage(), elapsed,
                    wall.currentCenter().x(), wall.currentCenter().z(), wall.radius(),
                    wall.nextCenter().x(), wall.nextCenter().z(), wall.nextRadius());
            if (phase == RavengardDeathWall.Phase.WARNING) {
                syncWorldBorder(wall);
                players.values().forEach(player -> {
                    player.sendMessage("<e>Death Wall stage {}/{} moves in {} seconds!",
                            wall.stage(), RavengardDeathWall.STAGE_COUNT, wall.phaseRemainingSeconds());
                    player.showBossBar(deathWallProgress);
                    RavengardSounds.play(player, RavengardSounds.Cue.DEATH_WALL_WARNING);
                });
            } else if (phase == RavengardDeathWall.Phase.MOVING) {
                players.values().forEach(player -> {
                    player.showBossBar(deathWallProgress);
                    RavengardSounds.play(player, RavengardSounds.Cue.DEATH_WALL_START);
                });
            }
        }
        if (wall.visible()) syncWorldBorder(wall);
        updateDeathWallPresentation(wall);
        if (!RavengardDeathWall.shouldDamage(elapsed, lastDeathWallDamageAt)) return;
        lastDeathWallDamageAt = elapsed;
        double radius = wall.radius();
        for (RavengardDungeonPlayer player : players.values()) {
            if (player.isDungeonDead() || player.getInstance() != instance) continue;
            boolean outside = RavengardDeathWall.isOutside(player.getPosition().x(), player.getPosition().z(),
                    wall.currentCenter().x(), wall.currentCenter().z(), radius);
            if (!outside) continue;
            player.sendActionBar(net.swofty.commons.text.Text.of("<c>Death Wall: return inside the safe zone"));
            float before = player.getHealth();
            boolean applied = RavengardDeathWall.applyUnmitigatedDamage(player, wall.damageAmount());
            org.tinylog.Logger.debug("Ravengard wall damage: game={} player={} elapsed={} progress={} amount={} position=({}, {}) bounds=[{}, {}]x[{}, {}] outside={} applied={} health={}->{}",
                    getGameId(), player.getUsername(), elapsed, wall.progress(), wall.damageAmount(),
                    player.getPosition().x(), player.getPosition().z(),
                    wall.currentCenter().x() - radius, wall.currentCenter().x() + radius,
                    wall.currentCenter().z() - radius, wall.currentCenter().z() + radius,
                    outside, applied, before, player.getHealth());
        }
    }

    private void updateDeathWallPresentation(RavengardDeathWall.State wall) {
        Component progressName = Component.empty();
        float progress = RavengardDeathWall.progressBar(wall);
        if (!deathWallProgress.name().equals(progressName)) deathWallProgress.name(progressName);
        if (deathWallProgress.progress() != progress) deathWallProgress.progress(progress);
        if (!wall.visible()) {
            players.values().forEach(player -> player.hideBossBar(deathWallProgress));
        } else {
            players.values().forEach(player -> player.showBossBar(deathWallProgress));
        }
    }

    private void syncWorldBorder(RavengardDeathWall.State wall) {
        WorldBorder current = RavengardDeathWall.border(wall);
        WorldBorder installed = syncedWorldBorder;
        boolean stageChanged = worldBorderStage != wall.stage();
        boolean phaseChanged = worldBorderPhase != wall.phase();
        boolean moving = wall.phase() == RavengardDeathWall.Phase.MOVING;
        if (!stageChanged && !phaseChanged && (!moving || lastWorldBorderSecond == wall.elapsedSeconds())) return;
        worldBorderStage = wall.stage();
        worldBorderPhase = wall.phase();
        lastWorldBorderSecond = wall.elapsedSeconds();
        syncedWorldBorder = current;
        players.values().stream()
                .filter(RavengardDungeonGame::isPlayReady)
                .forEach(player -> {
                    player.sendPacket(current.createCenterPacket());
                    if (moving && phaseChanged) {
                        long moveMillis = wall.phaseRemainingSeconds() * 1000L;
                        player.sendPacket(new net.minestom.server.network.packet.server.play.WorldBorderLerpSizePacket(
                                installed.diameter(), wall.nextRadius() * 2.0, moveMillis));
                    } else if (!moving && (stageChanged || phaseChanged)) {
                        player.sendPacket(current.createInitializePacket(current.diameter(), 0));
                    }
                });
    }

    public double getDeathWallRadius() {
        return getDeathWallState().radius();
    }

    public double getDeathWallCenterX() {
        return getDeathWallState().currentCenter().x();
    }

    public double getDeathWallCenterZ() {
        return getDeathWallState().currentCenter().z();
    }

    public boolean isDeathWallWarning() {
        return getDeathWallState().phase() == RavengardDeathWall.Phase.WARNING;
    }

    public boolean isDeathWallActive() {
        return state == GameState.IN_PROGRESS && getDeathWallState().visible();
    }

    private void startDeathWall() {
        resetDeathWall();
        lastWallPhase = null;
        RavengardDeathWall.State opening = RavengardDeathWall.state(0, deathWallTimeline);
        org.tinylog.Logger.info("Ravengard wall schedule: game={} source=official-playtest-timing firstSignal={} total={} initial=({}, {}, {}) final=({}, {}, {})",
                getGameId(), RavengardDeathWall.FIRST_WARNING_SECONDS,
                RavengardDeathWall.MATCH_DURATION_SECONDS,
                opening.currentCenter().x(), opening.currentCenter().z(), opening.radius(),
                deathWallTimeline.targetCenter(RavengardDeathWall.STAGE_COUNT).x(),
                deathWallTimeline.targetCenter(RavengardDeathWall.STAGE_COUNT).z(),
                deathWallTimeline.targetRadius(RavengardDeathWall.STAGE_COUNT));
    }

    void applyDeathWallForTest() {
        applyDeathWall();
    }

    void tickGameForTest() {
        tickGame();
    }

    public void extract(RavengardDungeonPlayer player) {
        if (state != GameState.IN_PROGRESS || player.isDungeonDead()
                || !players.containsKey(player.getUuid())) {
            rejectExtraction(player, "you are not in an active dungeon run");
            return;
        }
        if (!RavengardRunState.isActive(player.getUuid(), getGameId())) {
            rejectExtraction(player, "the server could not verify your active run");
            return;
        }
        try {
            if (!RavengardProfiles.secureExtraction(player)) {
                rejectExtraction(player, "your Ravengard profile could not be secured");
                return;
            }
        } catch (RuntimeException exception) {
            org.tinylog.Logger.error(exception,
                    "Ravengard extraction save failed: game={} player={}",
                    getGameId(), player.getUsername());
            rejectExtraction(player, "your loot could not be saved");
            return;
        }
        RavengardSounds.stop(player, RavengardSounds.Cue.DUNGEON_AMBIENCE);
        RavengardSounds.play(player, RavengardSounds.Cue.EXTRACTION_SUCCESS);
        RavengardRunState.finish(player.getUuid(), getGameId());
        removePlayerFromDungeon(player);
        resetDungeonState(player);
        player.sendMessage("<a>Extraction successful! Your loot has been secured.");
        org.tinylog.Logger.info("Ravengard extraction successful: game={} player={} remainingPlayers={}",
                getGameId(), player.getUsername(), players.size());
        player.sendTo(ServerType.RAVENGARD_LOBBY);
        if (players.values().stream().noneMatch(candidate -> !candidate.isDungeonDead())) {
            end();
            routePlayersToLobby(players.values().stream().toList());
        }
    }

    private void rejectExtraction(RavengardDungeonPlayer player, String reason) {
        RavengardSounds.stop(player, RavengardSounds.Cue.EXTRACTION_CHANNEL);
        RavengardSounds.play(player, RavengardSounds.Cue.EXTRACTION_FAIL);
        player.sendMessage("<c>Extraction failed: {}.", reason);
        org.tinylog.Logger.warn("Ravengard extraction rejected: game={} player={} state={} dead={} member={} activeRun={} reason={}",
                getGameId(), player.getUsername(), state, player.isDungeonDead(),
                players.containsKey(player.getUuid()),
                RavengardRunState.isActive(player.getUuid(), getGameId()), reason);
    }

    @Override
    public void end() {
        super.end();
        if (ambienceTask != null) ambienceTask.cancel();
    }

    @Override
    public void dispose() {
        if (gameTask != null) gameTask.cancel();
        if (ambienceTask != null) ambienceTask.cancel();
        resetDeathWall();
        for (RavengardDungeonPlayer player : players.values().stream().toList()) {
            removePlayerFromDungeon(player);
        }
        super.dispose();
    }

    private List<RavengardDungeonPlayer> teammatesOf(RavengardDungeonPlayer player) {
        return players.values().stream()
                .filter(candidate -> candidate.getDungeonTeam() == player.getDungeonTeam())
                .toList();
    }

    private List<RavengardDungeonPlayer> alivePlayers(int team) {
        return players.values().stream()
                .filter(player -> player.getDungeonTeam() == team && !player.isDungeonDead())
                .toList();
    }

    private Optional<RavengardDungeonPlayer> aliveTeammate(RavengardDungeonPlayer player) {
        return alivePlayers(player.getDungeonTeam()).stream()
                .filter(candidate -> !candidate.getUuid().equals(player.getUuid()))
                .findFirst();
    }

    private void lockTo(RavengardDungeonPlayer spectator, RavengardDungeonPlayer teammate) {
        spectator.setGameMode(GameMode.SPECTATOR);
        spectator.setSpectatingTeammate(teammate.getUuid());
        spectator.spectate(teammate);
    }

    private void enforceSpectators() {
        if (state != GameState.IN_PROGRESS) return;
        for (RavengardDungeonPlayer player : players.values()) {
            updateHud(player);
            if (!player.isDungeonDead() || player.getSpectatingTeammate() == null) continue;
            RavengardDungeonPlayer target = players.get(player.getSpectatingTeammate());
            if (target == null || target.isDungeonDead()) retargetSpectators(player.getDungeonTeam());
            else player.spectate(target);
        }
    }

    private void retargetSpectators(int team) {
        List<RavengardDungeonPlayer> alive = alivePlayers(team);
        if (alive.isEmpty()) {
            routeTeamToLobby(team);
            return;
        }
        for (RavengardDungeonPlayer player : players.values()) {
            if (player.getDungeonTeam() == team && player.isDungeonDead()) lockTo(player, alive.getFirst());
        }
    }

    private void routeTeamToLobby(int team) {
        routePlayersToLobby(players.values().stream()
                .filter(player -> player.getDungeonTeam() == team)
                .toList());
        if (players.isEmpty()) {
            end();
        }
    }

    private void routePlayersToLobby(List<RavengardDungeonPlayer> leavingPlayers) {
        for (RavengardDungeonPlayer player : leavingPlayers) {
            RavengardSounds.stop(player, RavengardSounds.Cue.DUNGEON_AMBIENCE);
            forfeitRun(player);
            if (!removePlayerFromDungeon(player)) continue;
            resetDungeonState(player);
            player.sendTo(ServerType.RAVENGARD_LOBBY);
        }
    }

    boolean detachForTransfer(java.util.UUID playerUuid) {
        return players.remove(playerUuid) != null;
    }

    static boolean ownsPlayerState(String ownerGameId, String playerGameId) {
        return ownerGameId.equals(playerGameId);
    }

    private boolean removePlayerFromDungeon(RavengardDungeonPlayer player) {
        if (players.remove(player.getUuid()) == null) return false;
        DungeonInstanceRegistry.markPlayerLeft(player.getUuid(), this);
        if (!ownsPlayerState(getGameId(), player.getGameId())) return false;
        player.setGameId(null);
        player.setSpectatingTeammate(null);
        return true;
    }

    private void resetDungeonState(RavengardDungeonPlayer player) {
        player.stopSpectating();
        player.setGameMode(GameMode.SURVIVAL);
        player.setDungeonDead(false);
        player.setDungeonTeam(-1);
        player.setSpectatingTeammate(null);
        var hud = RavengardHud.state(player);
        hud.setDungeon(false);
        hud.setSpectating(false);
        hud.setBossBarName(null);
        hud.setDeathWall(null);
        hud.setDeathWallWarning(null);
        hud.setTeamCount(0);
        hud.setPortalCount(0);
        hud.setKillCount(0);
    }

    private void updateHud(RavengardDungeonPlayer player) {
        updateHud(player, getDeathWallState());
    }

    private void updateHud(RavengardDungeonPlayer player, RavengardDeathWall.State wall) {
        var hud = RavengardHud.state(player);
        long seconds = state == GameState.IN_PROGRESS ? wall.remainingSeconds() : 0;
        hud.setClock(RavengardDeathWall.formatClock(seconds));
        hud.setPlayerCount(getAlivePlayerCount());
        hud.setTeamCount(getAliveTeamCount());
        hud.setPortalCount(extractionPoints.size());
        hud.setSpectating(player.isDungeonDead());
        hud.setDungeon(true);
        hud.setLocation(RavengardDeathWall.hudStatus(wall));
        boolean wallShown = state == GameState.IN_PROGRESS && wall.visible();
        hud.setBossBarName(wallShown ? RavengardDeathWall.status(wall) : null);
        hud.setDeathWall(wallShown
                ? new RavengardDeathWallView(wall.currentCenter().x(), wall.currentCenter().z(), wall.radius())
                : null);
        hud.setDeathWallWarning(wallShown && wall.hasNextZone()
                ? new RavengardDeathWallView(wall.nextCenter().x(), wall.nextCenter().z(), wall.nextRadius())
                : null);
    }

    private void resetDeathWall() {
        List<RavengardDungeonPlayer> readyPlayers = players.values().stream()
                .filter(RavengardDungeonGame::isPlayReady)
                .toList();
        readyPlayers.forEach(player -> player.hideBossBar(deathWallProgress));
        worldBorderStage = 0;
        worldBorderPhase = null;
        lastWorldBorderSecond = -1;
        syncedWorldBorder = WorldBorder.DEFAULT_BORDER;
        readyPlayers.forEach(player -> player.sendPacket(
                WorldBorder.DEFAULT_BORDER.createInitializePacket(WorldBorder.DEFAULT_BORDER.diameter(), 0)));
    }
}
