package dev.afkmod.testlab;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/** The steps of one scenario run, built with {@link Builder}. */
public record Script(List<Step> steps) {
	public Script {
		steps = List.copyOf(steps);
	}

	public static Builder builder() {
		return new Builder();
	}

	/** A one-step script: {@code action} must end the scenario (pass/fail/info). */
	public static Script of(Consumer<TestContext> action) {
		return builder().run(action).build();
	}

	public static final class Builder {
		private final List<Step> steps = new ArrayList<>();

		/** An instant step. */
		public Builder run(Consumer<TestContext> action) {
			Objects.requireNonNull(action);
			steps.add(ctx -> {
				action.accept(ctx);
				return true;
			});
			return this;
		}

		public Builder waitSeconds(double seconds) {
			return waitSeconds(ctx -> seconds);
		}

		/** Waits; the duration is computed when the step starts (e.g. from the effective config). */
		public Builder waitSeconds(ToDoubleFunction<TestContext> seconds) {
			steps.add(new Step() {
				private long start;
				private double duration;

				@Override
				public void start(TestContext ctx) {
					start = ctx.now();
					duration = seconds.applyAsDouble(ctx);
				}

				@Override
				public boolean tick(TestContext ctx) {
					return ctx.secondsSince(start) >= duration;
				}
			});
			return this;
		}

		/** Waits until {@code condition} holds; FAILs the scenario after {@code timeoutSeconds}. */
		public Builder waitUntil(String what, Predicate<TestContext> condition, double timeoutSeconds) {
			return waitUntil(what, condition, ctx -> timeoutSeconds, null);
		}

		public Builder waitUntil(String what, Predicate<TestContext> condition, ToDoubleFunction<TestContext> timeoutSeconds) {
			return waitUntil(what, condition, timeoutSeconds, null);
		}

		/**
		 * Waits until {@code condition} holds (it is also evaluated every tick, so it can sample or assert). After the
		 * timeout {@code onTimeout} runs (it usually ends the scenario); without one the scenario FAILs.
		 */
		public Builder waitUntil(String what, Predicate<TestContext> condition, ToDoubleFunction<TestContext> timeoutSeconds,
		                         Consumer<TestContext> onTimeout) {
			Objects.requireNonNull(condition);
			steps.add(new Step() {
				private long start;
				private double timeout;

				@Override
				public void start(TestContext ctx) {
					start = ctx.now();
					timeout = timeoutSeconds.applyAsDouble(ctx);
				}

				@Override
				public boolean tick(TestContext ctx) {
					if (condition.test(ctx)) return true;
					if (ctx.secondsSince(start) < timeout) return false;
					if (onTimeout != null) {
						onTimeout.accept(ctx);
						return true;
					}
					ctx.fail(String.format(Locale.ROOT, "Timed out after %.1f s waiting for %s (state %s)", timeout, what, ctx.state()));
					return true;
				}
			});
			return this;
		}

		/** Runs {@code action} at once and then every {@code everySeconds} until {@code seconds} have passed. */
		public Builder repeat(double seconds, double everySeconds, Consumer<TestContext> action) {
			return repeat(ctx -> seconds, everySeconds, action);
		}

		public Builder repeat(ToDoubleFunction<TestContext> seconds, double everySeconds, Consumer<TestContext> action) {
			if (everySeconds <= 0) throw new IllegalArgumentException("everySeconds must be positive");
			steps.add(new Step() {
				private long start;
				private double duration;
				private double nextAt;

				@Override
				public void start(TestContext ctx) {
					start = ctx.now();
					duration = seconds.applyAsDouble(ctx);
					nextAt = 0;
				}

				@Override
				public boolean tick(TestContext ctx) {
					double t = ctx.secondsSince(start);
					if (t >= duration) return true;
					if (t >= nextAt) {
						action.accept(ctx);
						nextAt += everySeconds;
					}
					return false;
				}
			});
			return this;
		}

		public Builder step(Step step) {
			steps.add(Objects.requireNonNull(step));
			return this;
		}

		public Script build() {
			return new Script(steps);
		}
	}
}
