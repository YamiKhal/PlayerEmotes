package com.yamikhal.playeremotes.client.animation;

import com.yamikhal.playeremotes.network.EmoteNetwork;

// where one of the two partner emote players is drawn: fixed spot and facing (Minecraft yaw degrees), so both
// line up as the animations were made however they stand, nobody gets moved
public record PartnerLink(int id, double x, double y, double z, float yaw) {

    // starter stands at the anchor facing the partner
    public static PartnerLink starter(EmoteNetwork.PartnerPlay play) {
        return new PartnerLink(play.id(), play.x(), play.y(), play.z(), play.yaw());
    }

    // partner stands distance blocks in front of the starter, facing back
    public static PartnerLink partner(EmoteNetwork.PartnerPlay play) {
        double yaw = Math.toRadians(play.yaw());
        return new PartnerLink(play.id(), play.x() - Math.sin(yaw) * play.distance(), play.y(),
                play.z() + Math.cos(yaw) * play.distance(), play.yaw() + 180);
    }
}
