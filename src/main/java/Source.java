import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ReflectPermission;
import java.security.Permissions;
import java.time.OffsetDateTime;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Base class for all execution strategies.
 */
@SuppressWarnings({"checkstyle:visibilitymodifier", "checkstyle:constantname"})
public abstract class Source implements Callable<Void> {
    /**
     * Compiler used.
     */
    protected String compiler = "";

    /**
     * How the source was run.
     */
    public String as = "";

    /**
     * Time that the source upload was received.
     */
    protected OffsetDateTime received;

    /**
     * Time that the result was returned to the client.
     */
    protected OffsetDateTime returned;

    /**
     * Time compilation started.
     */
    protected OffsetDateTime compileStarted;

    /**
     * Time compilation finished.
     */
    protected OffsetDateTime compileFinished;

    /**
     * Time compilation took in seconds.
     */
    protected double compileLength;

    /**
     * Whether compilation succeeded.
     */
    protected boolean compiled = false;

    /**
     * Error message generated by compilation if it failed.
     */
    protected String compilationErrorMessage;

    /**
     * Stack trace generated by compilation if it failed.
     */
    protected String compilationErrorStackTrace;

    /**
     * Time execution started.
     */
    protected OffsetDateTime executionStarted;

    /**
     * Time execution finished.
     */
    protected OffsetDateTime executionFinished;

    /**
     * Time execution took in seconds.
     */
    protected double executionLength;

    /**
     * Whether execution succeeded.
     */
    protected boolean executed = false;

    /**
     * Whether execution crashed.
     */
    protected boolean crashed = false;

    /**
     * Whether execution timed out.
     */
    protected boolean timedOut = false;

    /**
     * Error message generated by compilation if it failed.
     */
    protected String executionErrorMessage;

    /**
     * Stack trace generated by compilation if it failed.
     */
    protected String executionErrorStackTrace;

    /**
     * Default execution timeout in milliseconds.
     */
    public static final int DEFAULT_TIMEOUT = 100;

    /**
     * Execution timeout in milliseconds. Default is 100.
     */
    protected int timeoutLength = DEFAULT_TIMEOUT;

    /**
     * Output from execution if everything succeeded.
     */
    protected String output;

    /**
     * Current tool version.
     */
    protected static final String version = "0.3.1";

    /**
     * Gson object for serialization and deserialization.
     */
    private static transient Gson gson = new Gson();

    /**
     * Default permissions for code execution.
     */
    protected transient Permissions permissions = new Permissions();

    /**
     * Lock to serialize System.out and System.err.
     */
    private static final transient ReentrantLock OUTPUT_LOCK = new ReentrantLock();

    /**
     * Create a new Source object.
     */
    Source() {
        permissions.add(new RuntimePermission("getProtectionDomain"));
        permissions.add(new RuntimePermission("accessDeclaredMembers"));
        permissions.add(new ReflectPermission("suppressAccessChecks"));
    }

    /**
     * Create a new source object from a received JSON string.
     *
     * @param json  JSON string to initialize the new source object
     * @param klass subclass of Source to deserialize into
     * @return new source object initialized from the JSON string
     */
    protected static Source received(final String json, final Class<? extends Source> klass) {
        Source source = gson.fromJson(json, klass);
        source.received = OffsetDateTime.now();
        return source;
    }

    /**
     * Mark an execution as completed and generate JSON to return to the client.
     *
     * @return JSON string representing the result of this execution
     */
    public String completed() {
        returned = OffsetDateTime.now();
        return gson.toJson(this);
    }

    /**
     * Helper function to convert a stack track to a String.
     *
     * @param e Throwable to extract a stack trace from and convert it to a String
     * @return String containing the stack trace
     */
    protected static String stackTraceToString(final Throwable e) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        e.printStackTrace(printWriter);
        return stringWriter.toString();
    }

    /**
     * Called on each executor to actually do the compile.
     *
     * @throws Exception thrown if the compile fails
     */
    protected abstract void doCompile() throws Exception;

    /**
     * Compile the source to bytecode.
     * <p>
     * Throws an exception if compilation fails, with the type depending on how the code was being compiled.
     *
     * @return this object for chaining
     */
    public final Source compile() {
        try {
            compileStarted = OffsetDateTime.now();
            doCompile();
            compiled = true;
        } catch (Exception e) {
            compilationErrorMessage = e.toString();
            compilationErrorStackTrace = stackTraceToString(e);
        } finally {
            compileFinished = OffsetDateTime.now();
            compileLength = diffTimestamps(compileStarted, compileFinished);
        }
        return this;
    }

    /**
     * Called on each executor to actually run the code.
     *
     * @throws Exception thrown if execution fails
     */
    protected abstract void doExecute() throws Exception;

    @Override
    public final Void call() throws Exception {
        try {
            try {
                executionStarted = OffsetDateTime.now();
                doExecute();
                executed = true;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            } catch (Exception e) {
                throw e;
            }
        } catch (ThreadDeath e) {
        } catch (Throwable e) {
            crashed = true;
            executionErrorMessage = e.toString();
            executionErrorStackTrace = stackTraceToString(e);
        }
        return null;
    }

    /**
     * Execute some compiled Java code.
     *
     * @return this object for chaining
     */
    @SuppressWarnings("deprecation")
    public final Source execute() {
        if (!compiled) {
            return this;
        }
        OUTPUT_LOCK.lock();
        try {
            FutureTask<Void> futureTask = new FutureTask<>(this);
            Thread executionThread = new Thread(futureTask);


            ByteArrayOutputStream combinedOutputStream = new ByteArrayOutputStream();
            PrintStream combinedStream = new PrintStream(combinedOutputStream);
            PrintStream old = System.out;
            PrintStream err = System.err;
            System.setOut(combinedStream);
            System.setErr(combinedStream);

            try {
                executionThread.start();
                futureTask.get(timeoutLength, TimeUnit.MILLISECONDS);
                timedOut = false;
            } catch (TimeoutException e) {
                futureTask.cancel(true);
                executionThread.stop();
                timedOut = true;
            } catch (Throwable e) {
                timedOut = false;
            } finally {
                executionFinished = OffsetDateTime.now();
                executionLength = diffTimestamps(executionStarted, executionFinished);

                System.out.flush();
                System.err.flush();
                System.setOut(old);
                System.setErr(err);
                if (executed) {
                    output = combinedOutputStream.toString();
                }
            }
            return this;
        } finally {
            OUTPUT_LOCK.unlock();
        }
    }

    /**
     * Compile and execute sources. Convenience method for compile + execute.
     *
     * @return this object for chaining
     */
    public Source run() {
        return this.compile().execute();
    }

    /**
     * Convert milliseconds to seconds.
     */
    private static final double MILLISECONDS_TO_SECONDS = 1000.0;

    /**
     * Compute the difference of two timestamps in seconds.
     *
     * @param start the starting timestamp
     * @param end   the ending timestamp
     * @return the difference between the two timestamps in seconds
     */
    private static double diffTimestamps(final OffsetDateTime start, final OffsetDateTime end) {
        return (end.toInstant().toEpochMilli() - start.toInstant().toEpochMilli())
                / MILLISECONDS_TO_SECONDS;
    }

    @Override
    public final String toString() {
        return gson.toJson(this);
    }
}
