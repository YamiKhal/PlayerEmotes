package com.yamikhal.playeremotes.anim;

import java.util.Locale;

// easing curves named like the GeckoLib Blockbench plugin (easeInOutQuad, easeOutBack...), curves with a
// parameter read it from easingArgs, NaN means default
public enum Ease {
    LINEAR {
        @Override
        public double apply(double t, double arg) {
            return t;
        }
    },
    // jumps in arg (default 2) steps
    STEP {
        @Override
        public double apply(double t, double arg) {
            double steps = Double.isNaN(arg) ? 2 : Math.max(1, Math.round(arg));
            return Math.floor(t * steps) / steps;
        }
    },
    IN_SINE {
        @Override
        public double apply(double t, double arg) {
            return 1 - Math.cos(t * Math.PI / 2);
        }
    },
    OUT_SINE {
        @Override
        public double apply(double t, double arg) {
            return Math.sin(t * Math.PI / 2);
        }
    },
    IN_OUT_SINE {
        @Override
        public double apply(double t, double arg) {
            return -(Math.cos(Math.PI * t) - 1) / 2;
        }
    },
    IN_QUAD {
        @Override
        public double apply(double t, double arg) {
            return t * t;
        }
    },
    OUT_QUAD {
        @Override
        public double apply(double t, double arg) {
            return 1 - (1 - t) * (1 - t);
        }
    },
    IN_OUT_QUAD {
        @Override
        public double apply(double t, double arg) {
            return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
        }
    },
    IN_CUBIC {
        @Override
        public double apply(double t, double arg) {
            return t * t * t;
        }
    },
    OUT_CUBIC {
        @Override
        public double apply(double t, double arg) {
            return 1 - Math.pow(1 - t, 3);
        }
    },
    IN_OUT_CUBIC {
        @Override
        public double apply(double t, double arg) {
            return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
        }
    },
    IN_QUART {
        @Override
        public double apply(double t, double arg) {
            return t * t * t * t;
        }
    },
    OUT_QUART {
        @Override
        public double apply(double t, double arg) {
            return 1 - Math.pow(1 - t, 4);
        }
    },
    IN_OUT_QUART {
        @Override
        public double apply(double t, double arg) {
            return t < 0.5 ? 8 * t * t * t * t : 1 - Math.pow(-2 * t + 2, 4) / 2;
        }
    },
    IN_QUINT {
        @Override
        public double apply(double t, double arg) {
            return t * t * t * t * t;
        }
    },
    OUT_QUINT {
        @Override
        public double apply(double t, double arg) {
            return 1 - Math.pow(1 - t, 5);
        }
    },
    IN_OUT_QUINT {
        @Override
        public double apply(double t, double arg) {
            return t < 0.5 ? 16 * t * t * t * t * t : 1 - Math.pow(-2 * t + 2, 5) / 2;
        }
    },
    IN_EXPO {
        @Override
        public double apply(double t, double arg) {
            return t == 0 ? 0 : Math.pow(2, 10 * t - 10);
        }
    },
    OUT_EXPO {
        @Override
        public double apply(double t, double arg) {
            return t == 1 ? 1 : 1 - Math.pow(2, -10 * t);
        }
    },
    IN_OUT_EXPO {
        @Override
        public double apply(double t, double arg) {
            if (t == 0 || t == 1) {
                return t;
            }

            return t < 0.5 ? Math.pow(2, 20 * t - 10) / 2 : (2 - Math.pow(2, -20 * t + 10)) / 2;
        }
    },
    IN_CIRC {
        @Override
        public double apply(double t, double arg) {
            return 1 - Math.sqrt(1 - t * t);
        }
    },
    OUT_CIRC {
        @Override
        public double apply(double t, double arg) {
            return Math.sqrt(1 - Math.pow(t - 1, 2));
        }
    },
    IN_OUT_CIRC {
        @Override
        public double apply(double t, double arg) {
            return t < 0.5
                    ? (1 - Math.sqrt(1 - Math.pow(2 * t, 2))) / 2
                    : (Math.sqrt(1 - Math.pow(-2 * t + 2, 2)) + 1) / 2;
        }
    },
    // arg: overshoot (default 1.70158)
    IN_BACK {
        @Override
        public double apply(double t, double arg) {
            double c1 = overshoot(arg);
            return (c1 + 1) * t * t * t - c1 * t * t;
        }
    },
    OUT_BACK {
        @Override
        public double apply(double t, double arg) {
            return 1 - IN_BACK.apply(1 - t, arg);
        }
    },
    IN_OUT_BACK {
        @Override
        public double apply(double t, double arg) {
            return t < 0.5 ? IN_BACK.apply(2 * t, arg) / 2 : 1 - IN_BACK.apply(2 - 2 * t, arg) / 2;
        }
    },
    // arg: bounciness (default 1)
    IN_ELASTIC {
        @Override
        public double apply(double t, double arg) {
            return 1 - OUT_ELASTIC.apply(1 - t, arg);
        }
    },
    OUT_ELASTIC {
        @Override
        public double apply(double t, double arg) {
            if (t == 0 || t == 1) {
                return t;
            }

            double bounciness = Double.isNaN(arg) ? 1 : arg;
            double period = 0.3 / Math.max(0.1, bounciness);
            return Math.pow(2, -10 * t) * Math.sin((t - period / 4) * (2 * Math.PI) / period) + 1;
        }
    },
    IN_OUT_ELASTIC {
        @Override
        public double apply(double t, double arg) {
            return t < 0.5 ? IN_ELASTIC.apply(2 * t, arg) / 2 : 0.5 + OUT_ELASTIC.apply(2 * t - 1, arg) / 2;
        }
    },
    IN_BOUNCE {
        @Override
        public double apply(double t, double arg) {
            return 1 - OUT_BOUNCE.apply(1 - t, arg);
        }
    },
    OUT_BOUNCE {
        @Override
        public double apply(double t, double arg) {
            final double n1 = 7.5625;
            final double d1 = 2.75;
            if (t < 1 / d1) {
                return n1 * t * t;
            }

            if (t < 2 / d1) {
                return n1 * (t -= 1.5 / d1) * t + 0.75;
            }

            if (t < 2.5 / d1) {
                return n1 * (t -= 2.25 / d1) * t + 0.9375;
            }

            return n1 * (t -= 2.625 / d1) * t + 0.984375;
        }
    },
    IN_OUT_BOUNCE {
        @Override
        public double apply(double t, double arg) {
            return t < 0.5 ? (1 - OUT_BOUNCE.apply(1 - 2 * t, arg)) / 2 : (1 + OUT_BOUNCE.apply(2 * t - 1, arg)) / 2;
        }
    };

    // t is progress in [0, 1], arg optional curve parameter, NaN for default
    public abstract double apply(double t, double arg);

    // parses easeInOutQuad, ease_in_out_quad, linear, step...
    public static Ease byName(String name) {
        String key = name.toLowerCase(Locale.ROOT).replace("_", "");
        if (key.startsWith("ease")) {
            key = key.substring(4);
        }

        for (Ease ease : values()) {
            if (ease.name().toLowerCase(Locale.ROOT).replace("_", "").equals(key)) {
                return ease;
            }
        }

        return null;
    }

    private static double overshoot(double arg) {
        return Double.isNaN(arg) ? 1.70158 : arg;
    }
}
