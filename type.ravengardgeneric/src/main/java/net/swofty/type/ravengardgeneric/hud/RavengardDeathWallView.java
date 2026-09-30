package net.swofty.type.ravengardgeneric.hud;

public record RavengardDeathWallView(double centerX, double centerZ, double radius) {
    public int side() {
        return (int) Math.round(radius * 2.0);
    }
}
