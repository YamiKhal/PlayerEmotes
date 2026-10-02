package com.yamikhal.playeremotes.client.animation;

import com.yamikhal.playeremotes.network.EmoteNetwork;

// where one of the two players of a partner emote is drawn: at a fixed spot and facing (Minecraft yaw degrees),
// so both line up exactly as the animations were made however they actually stand, nobody is moved
public record PartnerLink(int id, double x, double y, double z, float yaw) {

    // the starter stands at the anchor facing the partner
    public static PartnerLink starter(EmoteNetwork.PartnerPlay play) {
        return new PartnerLink(play.id(), play.x(), play.y(), play.z(), play.yaw());
    }

    // the partner stands distance blocks in front of the starter, facing back
    public static PartnerLink partner(EmoteNetwork.PartnerPlay play) {
        double yaw = Math.toRadians(play.yaw());
        return new PartnerLink(play.id(), play.x() - Math.sin(yaw) * play.distance(), play.y(),
                play.z() + Math.cos(yaw) * play.distance(), play.yaw() + 180);
    }
}
