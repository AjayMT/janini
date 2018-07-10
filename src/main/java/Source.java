import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Permissions;
import java.time.OffsetDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Base class for all execution strategies.
 */
@SuppressWarnings({"checkstyle:visibilitymodifier", "checkstyle:constantname"})
public abstract class Source implements Callable<Boolean> {
    /** Compiler used. */
    protected String compiler;

    /** Time that the source upload was received. */
    protected OffsetDateTime received;

    /** Time that the result was returned to the client. */
    protected OffsetDateTime returned;

    /** Time compilation started. */
    protected OffsetDateTime compileStarted;

    /** Time compilation finished. */
    protected OffsetDateTime compileFinished;

    /** Whether compilation succeeded. */
    protected boolean compiled = false;

    /** Error message generated by compilation if it failed. */
    protected String compilationErrorMessage;

    /** Stack trace generated by compilation if it failed. */
    protected String compilationErrorStackTrace;

    /** Time compilation started. */
    protected OffsetDateTime executionStarted;

    /** Time compilation finished. */
    protected OffsetDateTime executionFinished;

    /** Whether compilation succeeded. */
    protected boolean executed = false;

    /** Whether compilation succeeded. */
    protected boolean timedOut = false;

    /** Error message generated by compilation if it failed. */
    protected String executionErrorMessage;

    /** Stack trace generated by compilation if it failed. */
    protected String executionErrorStackTrace;

    /** Default execution timeout in milliseconds. */
    public static final int DEFAULT_TIMEOUT = 100;

    /** Execution timeout in milliseconds. Default is 100. */
    protected int timeoutLength = DEFAULT_TIMEOUT;

    /** Output from execution if everything succeeded. */
    protected String output;

    /** Current tool version. */
    protected static final String version = "0.3.1";

    /** Gson object for serialization and deserialization. */
    private static transient Gson gson = new Gson();

    /** Default permissions for code execution. */
    protected transient Permissions permissions = new Permissions();

    /**
     * Create a new source object from a received JSON string.
     *
     * @param json JSON string to initialize the new source object
     * @param klass subclass of Source to deserialize into
     * @return new source object initialized from the JSON string
     */
    protected static Source received(final String json, final Class<? extends Source> klass) {
        Source source = gson.fromJson(json, klass);
        source.received = OffsetDateTime.now();
        source.permissions.add(new RuntimePermission("getProtectionDomain"));
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
     *
     * Throws an exception if compilation fails, with the type depending on how the code was being compiled.
     *
     * @throws Exception thrown if compilation fails
     */
    public void compile() throws Exception {
        try {
            compileStarted = OffsetDateTime.now();
            doCompile();
            compiled = true;
        } catch (Exception e) {
            compilationErrorMessage = e.toString();
            compilationErrorStackTrace = stackTraceToString(e);
            throw(e);
        } finally {
            compileFinished = OffsetDateTime.now();
        }
    }

    /**
     * Called on each executor to actually run the code.
     *
     * @throws Exception thrown if execution fails
     */
    protected abstract void doExecute() throws Exception;

    @Override
    public final Boolean call() throws Exception {
        try {
            executionStarted = OffsetDateTime.now();
            doExecute();
            executed = true;
        } catch (Exception e) {
            executionErrorMessage = e.toString();
            executionErrorStackTrace = stackTraceToString(e);
            throw(e);
        }
        return true;
    }

    /**
     * Execute some compiled Java code.
     */
    public synchronized void execute() {
        FutureTask<Boolean> futureTask = new FutureTask<>(this);
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
        } catch (Throwable e) {
            futureTask.cancel(true);
            executionThread.stop();
            timedOut = true;
            executionErrorMessage = e.toString();
            executionErrorStackTrace = stackTraceToString(e);
        } finally {
            executionFinished = OffsetDateTime.now();
            System.out.flush();
            System.err.flush();
            System.setOut(old);
            System.setErr(err);
            if (executed) {
                output = combinedOutputStream.toString();
            }
        }
    }
}