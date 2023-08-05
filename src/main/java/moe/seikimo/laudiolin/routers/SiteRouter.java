package moe.seikimo.laudiolin.routers;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.servlet.JavalinServletContext;
import moe.seikimo.laudiolin.Config;
import moe.seikimo.laudiolin.Laudiolin;
import moe.seikimo.laudiolin.utils.HttpUtils;

import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static moe.seikimo.laudiolin.utils.HttpUtils.RATE_LIMITED;

public interface SiteRouter {
    Map<String, Integer> RATE_LIMITS
            = new ConcurrentHashMap<>();
    List<String> RATE_LIMITED
            = new CopyOnWriteArrayList<>();

    /**
     * Configures the Javalin router.
     *
     * @param javalin The Javalin instance.
     */
    static void configure(Javalin javalin) {
        // Set the task for resetting rate limits.
        var limits = Config.get().rateLimits;
        var resetTime = TimeUnit.MILLISECONDS.convert(
                limits.getTimeToReset(), limits.getResetUnit());
        var withinTime = TimeUnit.MILLISECONDS.convert(
                limits.getWithinTime(), limits.getWithinUnit());

        new Timer().scheduleAtFixedRate(
                new ResetLimitsTask(), resetTime, resetTime);
        new Timer().scheduleAtFixedRate(
                new UpdateLimitsTask(), withinTime, withinTime);

        javalin.get("/", SiteRouter::redirect);
        javalin.before(SiteRouter::rateLimit);
    }

    /**
     * Redirects the user to the main site.
     *
     * @param ctx The context.
     */
    static void redirect(Context ctx) {
        ctx.redirect("https://laudiolin.seikimo.moe");
    }

    /**
     * Handles rate limiting.
     *
     * @param ctx The context.
     */
    static void rateLimit(Context ctx) {
        // Check if the context is a Javalin context.
        if (!(ctx instanceof JavalinServletContext context)) return;

        // Get the IP of the request.
        var ip = HttpUtils.ip(ctx);

        // Check if the IP is exempt from rate limiting.
        var limits = Config.get().rateLimits;
        if (limits.getExempt().contains(ip)) return;

        // Check if the user is already rate limited.
        if (RATE_LIMITED.contains(ip)) {
            ctx.status(429).json(RATE_LIMITED());
            context.getTasks().clear();
            return;
        }

        // Add the IP to the rate limit map.
        if (!RATE_LIMITS.containsKey(ip))
            RATE_LIMITS.put(ip, 1);

        // Get the current amount of requests.
        var max = limits.getMaxRequests();

        var requests = RATE_LIMITS.get(ip) + 1;
        var remaining = Math.max(0, max - requests);

        // Append headers.
        ctx.header("X-Rate-Limit", String.valueOf(max));
        ctx.header("X-Rate-Limit-Remaining", String.valueOf(remaining));

        // Check if the user has exceeded the rate limit.
        if (requests >= max) {
            // Cancel the response.
            ctx.status(429).json(RATE_LIMITED());
            context.getTasks().clear();

            // Add the user to the limit list.
            RATE_LIMITED.add(ip);
            // Clear the limits.
            RATE_LIMITS.remove(ip);
            return;
        }

        // Increment the amount of requests.
        RATE_LIMITS.put(ip, requests + 1);
    }

    /**
     * Handles Javalin/HTTP exceptions.
     *
     * @param exception The exception.
     * @param ctx The Javalin instance.
     */
    static void handleException(Exception exception, Context ctx) {
        ctx.status(500).result("Internal Server Error");
        Laudiolin.getLogger().warn("An exception occurred while handling a request.", exception);
    }

    /** Task used for clearing rate limiting. */
    final class ResetLimitsTask extends TimerTask {
        @Override
        public void run() {
            RATE_LIMITED.clear();
        }
    }

    /** Task used for checking rate limiting. */
    final class UpdateLimitsTask extends TimerTask {
        @Override
        public void run() {
            var limits = Config.get().rateLimits;
            RATE_LIMITS.forEach((ip, requests) -> {
                if (requests < limits.getMaxRequests()) {
                    RATE_LIMITS.remove(ip);
                } else {
                    RATE_LIMITED.add(ip);
                }
            });
        }
    }
}
