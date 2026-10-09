package com.yamikhal.playeremotes.anim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

// small Molang compiler for what usually goes into Blockbench keyframes: numbers, arithmetic, comparisons, logic,
// ternaries, math.* (trigonometry in degrees like Bedrock) and query.* / variable.* lookups through Context.
// compiled once on load, eval allocates nothing
public final class Molang {

    // emote files also come from servers, compile and eval recurse per nesting level and operator, so both capped
    // well below a stack overflow
    private static final int MAX_LENGTH = 1024;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_DIE_ROLLS = 64;

    private Molang() {}

    public static Expr constant(double value) {
        return new Constant(value);
    }

    // compiles an expression, constant parts get folded
    public static Expr compile(String source) {
        if (source.length() > MAX_LENGTH) {
            throw new MolangException("Expression longer than " + MAX_LENGTH + " characters");
        }

        Parser parser = new Parser(source);
        return parser.parseStatements();
    }

    // values for query.*, variable.* and other lookups
    public interface Context {

        // seconds since the animation started, wrapped for loops (query.anim_time)
        double animTime();

        // seconds since the emote started, never wrapped (query.life_time)
        double lifeTime();

        // player state (see Query), 0 without a player
        default double query(Query query) {
            return 0;
        }

        // any other lookup like variable.foo, unknown names return 0
        default double lookup(String name) {
            return 0;
        }
    }

    @FunctionalInterface
    public interface Expr {

        double eval(Context context);

        default boolean isConstant() {
            return false;
        }
    }

    public record Constant(double value) implements Expr {

        @Override
        public double eval(Context context) {
            return this.value;
        }

        @Override
        public boolean isConstant() {
            return true;
        }
    }

    public static final class MolangException extends RuntimeException {

        MolangException(String message) {
            super(message);
        }
    }

    private static final class Parser {

        private final String src;
        private int pos;
        private int depth;

        Parser(String src) {
            this.src = src;
        }

        Expr parseStatements() {
            // accept "return x;" and trailing semicolons of simple one-liners
            this.skipWhitespace();
            if (this.matchWord("return")) {
                this.skipWhitespace();
            }

            Expr expr = this.parseExpr();
            this.skipWhitespace();
            while (this.peek() == ';') {
                this.pos++;
                this.skipWhitespace();
            }

            if (this.pos < this.src.length()) {
                throw this.error("Unexpected '" + this.src.charAt(this.pos) + "'");
            }

            return expr;
        }

        // every nesting (parentheses, arguments, ternaries, unary) goes through here or parseUnary
        Expr parseExpr() {
            this.enter();
            Expr condition = this.parseNullish();
            this.skipWhitespace();
            if (this.peek() == '?' && this.peekAt(1) != '?') {
                this.pos++;
                Expr whenTrue = this.parseExpr();
                this.expect(':');
                Expr whenFalse = this.parseExpr();
                condition = ternary(condition, whenTrue, whenFalse);
            }

            this.depth--;
            return condition;
        }

        Expr parseNullish() {
            Expr left = this.parseOr();
            while (this.match("??")) {
                // values are never null, left side always wins
                this.parseOr();
            }

            return left;
        }

        Expr parseOr() {
            Expr left = this.parseAnd();
            while (this.match("||")) {
                Expr l = left;
                Expr r = this.parseAnd();
                left = op(l, r, c -> (l.eval(c) != 0 || r.eval(c) != 0) ? 1 : 0);
            }

            return left;
        }

        Expr parseAnd() {
            Expr left = this.parseEquality();
            while (this.match("&&")) {
                Expr l = left;
                Expr r = this.parseEquality();
                left = op(l, r, c -> (l.eval(c) != 0 && r.eval(c) != 0) ? 1 : 0);
            }

            return left;
        }

        Expr parseEquality() {
            Expr left = this.parseComparison();
            while (true) {
                Expr l = left;
                if (this.match("==")) {
                    Expr r = this.parseComparison();
                    left = op(l, r, c -> l.eval(c) == r.eval(c) ? 1 : 0);
                } else if (this.match("!=")) {
                    Expr r = this.parseComparison();
                    left = op(l, r, c -> l.eval(c) != r.eval(c) ? 1 : 0);
                } else {
                    return left;
                }
            }
        }

        Expr parseComparison() {
            Expr left = this.parseAdditive();
            while (true) {
                Expr l = left;
                if (this.match("<=")) {
                    Expr r = this.parseAdditive();
                    left = op(l, r, c -> l.eval(c) <= r.eval(c) ? 1 : 0);
                } else if (this.match(">=")) {
                    Expr r = this.parseAdditive();
                    left = op(l, r, c -> l.eval(c) >= r.eval(c) ? 1 : 0);
                } else if (this.match("<")) {
                    Expr r = this.parseAdditive();
                    left = op(l, r, c -> l.eval(c) < r.eval(c) ? 1 : 0);
                } else if (this.match(">")) {
                    Expr r = this.parseAdditive();
                    left = op(l, r, c -> l.eval(c) > r.eval(c) ? 1 : 0);
                } else {
                    return left;
                }
            }
        }

        Expr parseAdditive() {
            Expr left = this.parseMultiplicative();
            while (true) {
                Expr l = left;
                if (this.match("+")) {
                    Expr r = this.parseMultiplicative();
                    left = op(l, r, c -> l.eval(c) + r.eval(c));
                } else if (this.match("-")) {
                    Expr r = this.parseMultiplicative();
                    left = op(l, r, c -> l.eval(c) - r.eval(c));
                } else {
                    return left;
                }
            }
        }

        Expr parseMultiplicative() {
            Expr left = this.parseUnary();
            while (true) {
                Expr l = left;
                if (this.match("*")) {
                    Expr r = this.parseUnary();
                    left = op(l, r, c -> l.eval(c) * r.eval(c));
                } else if (this.match("/")) {
                    Expr r = this.parseUnary();
                    left = op(l, r, c -> {
                        double d = r.eval(c);
                        return d == 0 ? 0 : l.eval(c) / d;
                    });
                } else {
                    return left;
                }
            }
        }

        Expr parseUnary() {
            this.enter();
            Expr result;
            this.skipWhitespace();
            if (this.match("-")) {
                Expr e = this.parseUnary();
                result = e.isConstant() ? constant(-e.eval(null)) : c -> -e.eval(c);
            } else if (this.match("+")) {
                result = this.parseUnary();
            } else if (this.peek() == '!' && this.peekAt(1) != '=') {
                this.pos++;
                Expr e = this.parseUnary();
                result = e.isConstant() ? constant(e.eval(null) == 0 ? 1 : 0) : c -> e.eval(c) == 0 ? 1 : 0;
            } else {
                result = this.parsePrimary();
            }

            this.depth--;
            return result;
        }

        Expr parsePrimary() {
            this.skipWhitespace();
            char ch = this.peek();
            if (ch == '(') {
                this.pos++;
                Expr inner = this.parseExpr();
                this.expect(')');
                return inner;
            }

            if (Character.isDigit(ch) || ch == '.') {
                return constant(this.parseNumber());
            }

            if (Character.isLetter(ch) || ch == '_') {
                return this.parseResourceLocation();
            }

            throw this.error(ch == 0 ? "Unexpected end of expression" : "Unexpected '" + ch + "'");
        }

        private double parseNumber() {
            int start = this.pos;
            while (this.pos < this.src.length() && (Character.isDigit(this.src.charAt(this.pos)) || this.src.charAt(this.pos) == '.')) {
                this.pos++;
            }

            if (this.pos < this.src.length() && (this.src.charAt(this.pos) == 'f' || this.src.charAt(this.pos) == 'F')) {
                String number = this.src.substring(start, this.pos++);
                return Double.parseDouble(number);
            }

            return Double.parseDouble(this.src.substring(start, this.pos));
        }

        private Expr parseResourceLocation() {
            int start = this.pos;
            while (this.pos < this.src.length()) {
                char c = this.src.charAt(this.pos);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                    this.pos++;
                } else {
                    break;
                }
            }

            String name = normalize(this.src.substring(start, this.pos).toLowerCase(Locale.ROOT));
            this.skipWhitespace();

            List<Expr> args = new ArrayList<>();
            if (this.peek() == '(') {
                this.pos++;
                this.skipWhitespace();
                if (this.peek() != ')') {
                    do {
                        args.add(this.parseExpr());
                        this.skipWhitespace();
                    } while (this.match(","));
                }

                this.expect(')');
            }

            return this.resolve(name, args);
        }

        private Expr resolve(String name, List<Expr> a) {
            return switch (name) {
                case "query.anim_time" -> Context::animTime;
                case "query.life_time" -> Context::lifeTime;
                case "math.pi" -> constant(Math.PI);
                case "true" -> constant(1);
                case "false" -> constant(0);
                case "math.sin" -> this.fn1(a, x -> Math.sin(Math.toRadians(x)));
                case "math.cos" -> this.fn1(a, x -> Math.cos(Math.toRadians(x)));
                case "math.asin" -> this.fn1(a, x -> Math.toDegrees(Math.asin(x)));
                case "math.acos" -> this.fn1(a, x -> Math.toDegrees(Math.acos(x)));
                case "math.atan" -> this.fn1(a, x -> Math.toDegrees(Math.atan(x)));
                case "math.atan2" -> this.fn2(a, (y, x) -> Math.toDegrees(Math.atan2(y, x)));
                case "math.abs" -> this.fn1(a, Math::abs);
                case "math.sqrt" -> this.fn1(a, Math::sqrt);
                case "math.exp" -> this.fn1(a, Math::exp);
                case "math.ln" -> this.fn1(a, Math::log);
                case "math.floor" -> this.fn1(a, Math::floor);
                case "math.ceil" -> this.fn1(a, Math::ceil);
                case "math.round" -> this.fn1(a, x -> (double) Math.round(x));
                case "math.trunc" -> this.fn1(a, x -> x < 0 ? Math.ceil(x) : Math.floor(x));
                case "math.sign" -> this.fn1(a, Math::signum);
                case "math.hermite_blend" -> this.fn1(a, x -> 3 * x * x - 2 * x * x * x);
                case "math.pow" -> this.fn2(a, Math::pow);
                case "math.min" -> this.fn2(a, Math::min);
                case "math.max" -> this.fn2(a, Math::max);
                case "math.mod" -> this.fn2(a, (x, y) -> y == 0 ? 0 : x % y);
                case "math.clamp" -> this.fn3(a, (x, lo, hi) -> Math.max(lo, Math.min(hi, x)));
                case "math.lerp" -> this.fn3(a, (x, y, t) -> x + (y - x) * t);
                case "math.lerprotate" -> this.fn3(a, (x, y, t) -> x + wrapDegrees(y - x) * t);
                case "math.random" -> this.fn2(a, (lo, hi) -> lo + ThreadLocalRandom.current().nextDouble() * (hi - lo));
                case "math.random_integer" -> this.fn2(a, (lo, hi) ->
                        (double) (long) (lo + Math.floor(ThreadLocalRandom.current().nextDouble() * (hi - lo + 1))));
                case "math.min_angle" -> this.fn1(a, Parser::wrapDegrees);
                case "math.die_roll" -> this.fn3(a, (n, lo, hi) -> dieRoll(n, lo, hi, false));
                case "math.die_roll_integer" -> this.fn3(a, (n, lo, hi) -> dieRoll(n, lo, hi, true));
                default -> {
                    if (name.startsWith("math.")) {
                        throw this.error("Unknown function '" + name + "'");
                    }

                    Query query = Query.byName(name);
                    if (query != null) {
                        yield c -> c.query(query);
                    }

                    // custom queries of other mods (AzureLib query.my_charge(1)) take arguments, unknown means 0
                    yield c -> c.lookup(name);
                }
            };
        }

        private Expr fn1(List<Expr> a, F1 f) {
            this.arity(a, 1);
            Expr x = a.get(0);
            return x.isConstant() ? constant(f.apply(x.eval(null))) : c -> f.apply(x.eval(c));
        }

        private Expr fn2(List<Expr> a, F2 f) {
            this.arity(a, 2);
            Expr x = a.get(0);
            Expr y = a.get(1);
            return c -> f.apply(x.eval(c), y.eval(c));
        }

        private Expr fn3(List<Expr> a, F3 f) {
            this.arity(a, 3);
            Expr x = a.get(0);
            Expr y = a.get(1);
            Expr z = a.get(2);
            return c -> f.apply(x.eval(c), y.eval(c), z.eval(c));
        }

        private void arity(List<Expr> args, int count) {
            if (args.size() != count) {
                throw this.error("Expected " + count + " argument(s), got " + args.size());
            }
        }

        private void enter() {
            if (++this.depth > MAX_DEPTH) {
                throw this.error("Expression nested deeper than " + MAX_DEPTH + " levels");
            }
        }

        private boolean match(String token) {
            this.skipWhitespace();
            if (this.src.startsWith(token, this.pos)) {
                this.pos += token.length();
                return true;
            }

            return false;
        }

        private boolean matchWord(String word) {
            if (this.src.regionMatches(true, this.pos, word, 0, word.length())
                    && (this.pos + word.length() >= this.src.length() || !Character.isLetterOrDigit(this.src.charAt(this.pos + word.length())))) {
                this.pos += word.length();
                return true;
            }

            return false;
        }

        private void expect(char c) {
            this.skipWhitespace();
            if (this.peek() != c) {
                throw this.error("Expected '" + c + "'");
            }

            this.pos++;
        }

        private char peek() {
            return this.pos < this.src.length() ? this.src.charAt(this.pos) : 0;
        }

        private char peekAt(int offset) {
            return this.pos + offset < this.src.length() ? this.src.charAt(this.pos + offset) : 0;
        }

        private void skipWhitespace() {
            while (this.pos < this.src.length() && Character.isWhitespace(this.src.charAt(this.pos))) {
                this.pos++;
            }
        }

        private MolangException error(String message) {
            return new MolangException(message + " at " + this.pos + " in \"" + this.src + "\"");
        }

        private static Expr ternary(Expr condition, Expr a, Expr b) {
            if (condition.isConstant()) {
                return condition.eval(null) != 0 ? a : b;
            }

            return c -> condition.eval(c) != 0 ? a.eval(c) : b.eval(c);
        }

        private static Expr op(Expr l, Expr r, Expr result) {
            if (l.isConstant() && r.isConstant()) {
                return constant(result.eval(null));
            }

            return result;
        }

        private static String normalize(String name) {
            if (name.startsWith("q.")) {
                return "query." + name.substring(2);
            }

            if (name.startsWith("v.")) {
                return "variable." + name.substring(2);
            }

            if (name.startsWith("t.")) {
                return "temp." + name.substring(2);
            }

            if (name.startsWith("c.")) {
                return "context." + name.substring(2);
            }

            return name;
        }

        // sum of count random numbers between low and high, count capped since files also come from servers
        private static double dieRoll(double count, double low, double high, boolean integer) {
            int rolls = (int) Math.max(0, Math.min(MAX_DIE_ROLLS, count));
            double sum = 0;
            for (int i = 0; i < rolls; i++) {
                double roll = ThreadLocalRandom.current().nextDouble();
                sum += integer ? Math.ceil(low) + Math.floor(roll * (Math.floor(high) - Math.ceil(low) + 1)) : low + roll * (high - low);
            }

            return sum;
        }

        private static double wrapDegrees(double d) {
            d %= 360;
            if (d >= 180) {
                d -= 360;
            }

            if (d < -180) {
                d += 360;
            }

            return d;
        }

        private interface F1 {

            double apply(double x);
        }

        private interface F2 {

            double apply(double x, double y);
        }

        private interface F3 {

            double apply(double x, double y, double z);
        }
    }
}
