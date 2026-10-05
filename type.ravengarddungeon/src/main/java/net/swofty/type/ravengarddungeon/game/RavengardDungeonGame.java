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
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class RavengardDungeonGame extends AbstractGame<RavengardDungeonPlayer> {
    private volatile net.swofty.type.ravengardgeneric.queue.RavengardMatchMode matchMode =
            net.swofty.type.ravengardgeneric.queue.RavengardMatchMode.TRIOS;
    /** Players who queued as a party, by their party leader, so they land on one team. */
    private final Map<UUID, UUID> partyLeaders = new java.util.concurrent.ConcurrentHashMap<>();
    static final int COUNTDOWN_SECONDS = 60;
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
    private int startDeferrals;
    private static final int START_DEFERRAL_TICKS = 20;
    private static final int MAX_START_DEFERRALS = 10;
    private java.util.function.IntSupplier pendingJoins = () -> 0;

    /** How many players hold a reservation for this game but have not joined it yet. */
    public void setPendingJoins(java.util.function.IntSupplier pendingJoins) {
        this.pendingJoins = pendingJoins == null ? () -> 0 : pendingJoins;
    }
    private long lastSpectatorSecond = -1;
    private RavengardDeathWall.Phase lastWallPhase;
    private int worldBorderStage;
    private RavengardDeathWall.Phase worldBorderPhase;
    private long lastWorldBorderSecond = -1;
    private WorldBorder syncedWorldBorder = WorldBorder.DEFAULT_BORDER;
    private volatile RavengardDeathWall.State deathWallState;
    private volatile ExtractionPortals portals;
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
            player.sendMessage("<7>游戏将在 <e>{}<7> 秒后开始！", seconds);
            RavengardSounds.play(player, RavengardSounds.Cue.MATCH_COUNTDOWN);
        }
    }

    public RavengardDungeonConfig getConfig() {
        return config;
    }

    public List<Pos> getExtractionPoints() {
        return extractionPoints;
    }

    /** The escape portals open right now. */
    public List<Pos> getOpenExtractionPoints() {
        ExtractionPortals current = portals;
        return current == null ? List.of() : current.openAnchors();
    }

    void announcePortals(String message) {
        for (RavengardDungeonPlayer player : players.values()) {
            player.sendMessage(message);
        }
        portalsChanged();
    }

    void portalsChanged() {
        players.values().forEach(this::updateHud);
    }

    private void openPortalSchedule() {
        closePortals();
        List<ExtractionPortals.Wave> waves = ExtractionPortals.plan(
                extractionPoints, deathWallTimeline,
                anchor -> ExtractionPortals.setAround(anchor,
                        net.swofty.type.ravengarddungeon.generator.DungeonTemplate.get()::blockAt));
        portals = new ExtractionPortals(instance, this, waves);
        org.tinylog.Logger.info("Ravengard escape portal plan: game={} waves={}", getGameId(),
                waves.stream().map(wave -> wave.opensAt() + "s:" + wave.sites().stream()
                        .map(site -> "(" + site.anchor().blockX() + "," + site.anchor().blockZ() + ")x"
                                + site.portals().size())
                        .toList()).toList());
    }

    private void closePortals() {
        ExtractionPortals current = portals;
        portals = null;
        if (current != null) current.close();
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
            return new Game.JoinResult.Denied("玩家连接尚未就绪");
        }
        Game.JoinResult result = super.join(player);
        if (result instanceof Game.JoinResult.Success) {
            int playerIndex = players.size() - 1;
            int team = openTeam(player);
            player.setDungeonTeam(team);
            player.setDungeonDead(false);
            RavengardRunState.begin(player.getUuid(), getGameId());
            Pos spawn = spawnPoints.get((matchMode.teamSize() > 1 ? team : playerIndex) % spawnPoints.size());
            announceJoin(player);
            player.setRespawnPoint(spawn);
            players.values().forEach(this::updateHud);
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

    /** A party member joins their party's team; anyone else fills the first team with room left over by parties. */
    int openTeam(RavengardDungeonPlayer joining) {
        int teamSize = matchMode.teamSize();
        int[] sizes = new int[Math.max(1, (getMaxPlayers() + teamSize - 1) / teamSize)];
        Map<Integer, UUID> teamParty = new java.util.HashMap<>();
        for (RavengardDungeonPlayer member : players.values()) {
            if (member == joining || member.getDungeonTeam() < 0 || member.getDungeonTeam() >= sizes.length) continue;
            sizes[member.getDungeonTeam()]++;
            UUID leader = partyLeaders.get(member.getUuid());
            if (leader != null) teamParty.put(member.getDungeonTeam(), leader);
        }
        UUID party = partyLeaders.get(joining.getUuid());
        if (party != null) {
            for (int team = 0; team < sizes.length; team++) {
                if (party.equals(teamParty.get(team)) && sizes[team] < teamSize) return team;
            }
            for (int team = 0; team < sizes.length; team++) {
                if (sizes[team] == 0) return team;
            }
        }
        for (int team = 0; team < sizes.length; team++) {
            if (sizes[team] < teamSize && !teamParty.containsKey(team)) return team;
        }
        for (int team = 0; team < sizes.length; team++) {
            if (sizes[team] < teamSize) return team;
        }
        return 0;
    }

    public void setMatchMode(net.swofty.type.ravengardgeneric.queue.RavengardMatchMode mode) {
        this.matchMode = mode;
    }

    public net.swofty.type.ravengardgeneric.queue.RavengardMatchMode getMatchMode() {
        return matchMode;
    }

    public void setPartyLeader(UUID player, UUID leader) {
        if (leader == null) partyLeaders.remove(player);
        else partyLeaders.put(player, leader);
    }

    private void announceJoin(RavengardDungeonPlayer joined) {
        int count = players.size();
        for (RavengardDungeonPlayer viewer : players.values()) {
            viewer.sendMessage("<e>{}<7> 加入了游戏！（<a>{}<7>/<a>{}<7>）",
                    joined.getUsername(), count, getMaxPlayers());
        }
    }

    @Override
    public int getMaxPlayers() {
        return matchMode.maxPlayers();
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
        return CountdownConfig.simple(COUNTDOWN_SECONDS);
    }

    @Override
    public void start() {
        if (state != GameState.WAITING && state != GameState.COUNTDOWN) return;
        if (pendingJoins.getAsInt() > 0 && startDeferrals++ < MAX_START_DEFERRALS) {
            MinecraftServer.getSchedulerManager().buildTask(this::start)
                    .delay(TaskSchedule.tick(START_DEFERRAL_TICKS)).schedule();
            return;
        }
        startDeferrals = 0;
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
                viewer.sendMessage(victimColor + "{} <7>被 <f>{}<7> 击败！",
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

    public boolean hasFallenTeammates(RavengardDungeonPlayer reviver) {
        return canInteract(reviver) && !fallenTeammates(reviver).isEmpty();
    }

    /** Brings the reviver's fallen teammates back at a Revival Statue; returns how many came back. */
    public int reviveTeammates(RavengardDungeonPlayer reviver, Pos at) {
        if (!canInteract(reviver)) return 0;
        List<RavengardDungeonPlayer> fallen = fallenTeammates(reviver);
        for (RavengardDungeonPlayer revived : fallen) {
            revived.stopSpectating();
            revived.setSpectatingTeammate(null);
            revived.setDungeonDead(false);
            revived.setGameMode(GameMode.SURVIVAL);
            revived.heal();
            revived.teleport(at);
            RavengardSounds.play(revived, RavengardSounds.Cue.REVIVE);
            for (RavengardDungeonPlayer teammate : teammatesOf(reviver)) {
                teammate.sendMessage("<a>{} <a>复活了 <f>{}<a>！", reviver.getUsername(), revived.getUsername());
            }
        }
        if (!fallen.isEmpty()) {
            RavengardSounds.play(reviver, RavengardSounds.Cue.REVIVE);
            players.values().forEach(this::updateHud);
            org.tinylog.Logger.info("Ravengard revival: game={} reviver={} revived={}", getGameId(),
                    reviver.getUsername(), fallen.stream().map(RavengardDungeonPlayer::getUsername).toList());
        }
        return fallen.size();
    }

    private List<RavengardDungeonPlayer> fallenTeammates(RavengardDungeonPlayer reviver) {
        return teammatesOf(reviver).stream()
                .filter(candidate -> !candidate.getUuid().equals(reviver.getUuid()))
                .filter(RavengardDungeonPlayer::isDungeonDead)
                .filter(candidate -> candidate.getInstance() == instance)
                .toList();
    }

    public boolean canInteract(RavengardDungeonPlayer player) {
        return state == GameState.IN_PROGRESS && startedAtNanos != 0
                && players.containsKey(player.getUuid()) && !player.isDungeonDead()
                && RavengardRunState.isActive(player.getUuid(), getGameId());
    }

    public boolean isParticipant(java.util.UUID playerUuid) {
        return players.containsKey(playerUuid);
    }

    public boolean restoreRun(RavengardDungeonPlayer player) {
        if (state != GameState.IN_PROGRESS || !players.containsKey(player.getUuid())
                || player.isDungeonDead() || RavengardRunState.isActive(player.getUuid())) {
            return false;
        }
        RavengardRunState.begin(player.getUuid(), getGameId());
        return true;
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
            boolean participant = players.containsKey(player.getUuid());
            removePlayerFromDungeon(player);
            if (!participant) return;
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
            case WAITING -> "等待游戏开始";
            case COUNTDOWN -> "游戏即将开始";
            case IN_PROGRESS -> RavengardDeathWall.status(getDeathWallState());
            default -> "地牢已结束";
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
        killFallenIntoTheVoid();
        applyDeathWall();
        ExtractionPortals current = portals;
        if (current != null && state == GameState.IN_PROGRESS) current.tick(getElapsedSeconds());
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
            }
            routePlayersToLobby(unready);
            if (players.isEmpty()) {
                end();
                return false;
            }
        }
        startedAtNanos = System.nanoTime();
        startDeathWall();
        openPortalSchedule();
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
                player.sendMessage("<c>死亡之墙吞没了地牢。你没有及时撤离。");
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
                    player.sendMessage("<e>死亡之墙第 {}/{} 阶段将在 {} 秒后移动！",
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
            player.sendActionBar(net.swofty.commons.text.Text.of("<c>死亡之墙：请回到安全区内"));
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

    /** Whether the player escaped (and so used up the portal). */
    public boolean extract(RavengardDungeonPlayer player) {
        if (state != GameState.IN_PROGRESS || player.isDungeonDead()
                || !players.containsKey(player.getUuid())) {
            rejectExtraction(player, "你不在进行中的地牢探险里");
            return false;
        }
        if (!RavengardRunState.isActive(player.getUuid(), getGameId())) {
            rejectExtraction(player, "服务器无法确认你的探险状态");
            return false;
        }
        try {
            if (!RavengardProfiles.secureExtraction(player)) {
                rejectExtraction(player, "无法保存你的天穹余烬存档");
                return false;
            }
        } catch (RuntimeException exception) {
            org.tinylog.Logger.error(exception,
                    "Ravengard extraction save failed: game={} player={}",
                    getGameId(), player.getUsername());
            rejectExtraction(player, "无法保存你的战利品");
            return false;
        }
        RavengardSounds.stop(player, RavengardSounds.Cue.DUNGEON_AMBIENCE);
        RavengardSounds.play(player, RavengardSounds.Cue.EXTRACTION_SUCCESS);
        RavengardRunState.finish(player.getUuid(), getGameId());
        removePlayerFromDungeon(player);
        resetDungeonState(player);
        player.sendMessage("<a>撤离成功！你的战利品已保存。");
        org.tinylog.Logger.info("Ravengard extraction successful: game={} player={} remainingPlayers={}",
                getGameId(), player.getUsername(), players.size());
        player.sendTo(ServerType.RAVENGARD_LOBBY);
        if (players.values().stream().noneMatch(candidate -> !candidate.isDungeonDead())) {
            end();
            routePlayersToLobby(players.values().stream().toList());
        }
        return true;
    }

    private void rejectExtraction(RavengardDungeonPlayer player, String reason) {
        RavengardSounds.stop(player, RavengardSounds.Cue.EXTRACTION_CHANNEL);
        RavengardSounds.play(player, RavengardSounds.Cue.EXTRACTION_FAIL);
        player.sendMessage("<c>撤离失败：{}。", reason);
        org.tinylog.Logger.warn("Ravengard extraction rejected: game={} player={} state={} dead={} member={} activeRun={} reason={}",
                getGameId(), player.getUsername(), state, player.isDungeonDead(),
                players.containsKey(player.getUuid()),
                RavengardRunState.isActive(player.getUuid(), getGameId()), reason);
    }

    @Override
    public void end() {
        super.end();
        if (ambienceTask != null) ambienceTask.cancel();
        closePortals();
    }

    @Override
    public void dispose() {
        if (gameTask != null) gameTask.cancel();
        if (ambienceTask != null) ambienceTask.cancel();
        resetDeathWall();
        for (RavengardDungeonPlayer player : players.values().stream().toList()) {
            forfeitRun(player);
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

    /** Below the dungeon floor there is nothing but void; anyone falling past it dies there. */
    static final double VOID_Y = 40.0;

    private void killFallenIntoTheVoid() {
        for (RavengardDungeonPlayer player : players.values()) {
            if (player.isDungeonDead() || player.isDead() || player.getPosition().y() >= VOID_Y) continue;
            if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) continue;
            player.damage(net.minestom.server.entity.damage.DamageType.OUT_OF_WORLD, Float.MAX_VALUE);
        }
    }

    private void enforceSpectators() {
        if (state != GameState.IN_PROGRESS) return;
        long second = getElapsedSeconds();
        boolean refreshCamera = second != lastSpectatorSecond;
        lastSpectatorSecond = second;
        for (RavengardDungeonPlayer player : players.values()) {
            if (!player.isDungeonDead() || player.getSpectatingTeammate() == null) continue;
            RavengardDungeonPlayer target = players.get(player.getSpectatingTeammate());
            if (target == null || target.isDungeonDead()) {
                retargetSpectators(player.getDungeonTeam());
            } else if (refreshCamera) {
                player.teleport(target.getPosition()).thenRun(() -> player.spectate(target));
            }
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
        ExtractionPortals current = portals;
        hud.setPortalCount(current == null ? 0 : current.openCount());
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
