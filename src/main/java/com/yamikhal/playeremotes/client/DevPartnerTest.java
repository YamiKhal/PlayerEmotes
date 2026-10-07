package com.yamikhal.playeremotes.client;

import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.client.emote.Emote;
import com.yamikhal.playeremotes.client.emote.EmoteRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.File;

// dev helper for the two client run: with playeremotes-partnertest in the game directory, PlayerA starts a high
// five once PlayerB is in view, PlayerB accepts, both take screenshots (server lines them up, see DevPartnerSetup)
final class DevPartnerTest {

    private static final int START_DELAY = 100;

    private static int ticks = -1;
    private static int partnerTicks = -1;
    private static int shots;

    private DevPartnerTest() {}

    static void tick(Minecraft minecraft) {
        if (ticks == Integer.MAX_VALUE) {
            return;
        }

        if (ticks < 0) {
            if (minecraft.player == null || minecraft.level == null) {
                return;
            }

            if (!new File(minecraft.gameDirectory, "playeremotes-partnertest").exists()) {
                ticks = Integer.MAX_VALUE;
                return;
            }

            // both players need to see each other
            if (minecraft.level.players().size() < 2) {
                return;
            }

            ticks = 0;
        }

        // left the world during the test
        if (minecraft.player == null || minecraft.level == null) {
            ticks = Integer.MAX_VALUE;
            return;
        }

        ticks++;
        boolean starter = minecraft.player.getScoreboardName().equals("PlayerA");
        if (starter && ticks == START_DELAY) {
            Emote emote = EmoteRegistry.get(PlayerEmotes.id("high_five"));
            if (emote != null) {
                PlayerEmotesClient.play(emote);
            }
        } else if (starter && ticks == START_DELAY + 20) {
            shot(minecraft, "waiting");
        } else if (!starter && ticks == START_DELAY + 40) {
            shot(minecraft, "request");
            PlayerEmotesClient.acceptPartner();
        }

        if (LocalEmotes.partnership() != 0 && partnerTicks < 0) {
            partnerTicks = 0;
        }

        if (partnerTicks >= 0) {
            partnerTicks++;
            // side view, looking around does not turn the drawn body, it stays aligned with the partner
            if (!starter && partnerTicks == 2) {
                minecraft.player.setYRot(minecraft.player.getYRot() + 90);
            }

            if (partnerTicks == 6 || partnerTicks == 14) {
                shot(minecraft, "partner");
            }
        }

        // then a prop emote from the server packs (if any), the other player syncs with it
        if (partnerTicks == 60) {
            if (starter) {
                Emote prop = EmoteRegistry.get(PlayerEmotes.id("emotetest", "server_spin"));
                if (prop == null) {
                    prop = EmoteRegistry.get(PlayerEmotes.id("robot_dance"));
                }

                if (prop != null) {
                    PlayerEmotesClient.play(prop);
                }
            }
        } else if (partnerTicks == 75 && !starter) {
            minecraft.level.players().stream().filter(other -> other != minecraft.player).findFirst()
                    .ifPresent(other -> PlayerEmotesClient.syncWith(other.getUUID()));
        } else if (partnerTicks == 85) {
            shot(minecraft, "prop + sync");
        }

        if (shots >= 4 || ticks > START_DELAY + 400) {
            PlayerEmotes.LOGGER.info("[partnertest] done after {} shots", shots);
            ticks = Integer.MAX_VALUE;
        }
    }

    private static void shot(Minecraft minecraft, String what) {
        shots++;
        PlayerEmotes.LOGGER.info("[partnertest] screenshot {} ({})", shots, what);
        //? if >=26.3 {
        /*Screenshot.grab(minecraft, false);
        *///?} else
        Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(), message -> {});
    }
}
