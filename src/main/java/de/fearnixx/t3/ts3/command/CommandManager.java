package de.fearnixx.t3.ts3.command;

import de.fearnixx.t3.Main;
import de.fearnixx.t3.event.query.IQueryEvent;
import de.fearnixx.t3.reflect.annotation.Listener;
import de.mlessmann.logging.ILogReceiver;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by MarkL4YG on 08-Nov-17
 */
public class CommandManager implements ICommandManager {

    public static final String COMMAND_PREFIX = "!";
    public static final Integer THREAD_POOL_SIZE = 5;
    public static Integer AWAIT_TERMINATION_DELAY = 5000;

    private ILogReceiver log;

    private Map<String, ICommandReceiver> commands;
    private boolean terminated = false;
    private final Object lock = new Object();

    private ExecutorService executorSvc;

    public CommandManager(ILogReceiver log) {
        this.log = log;
        commands = new HashMap<>();
        executorSvc = Executors.newFixedThreadPool(Main.getProperty("bot.commandmgr.poolsize", THREAD_POOL_SIZE));
        AWAIT_TERMINATION_DELAY = Main.getProperty("bot.commandmgr.terminatedelay", AWAIT_TERMINATION_DELAY);
    }

    /**
     * The CommandManager actually uses given functionality and parses TextMessages starting with "!"
     *
     * Actual execution is done asynchronously!
     * @param event The event
     */
    @Listener
    public void onTextMessage(IQueryEvent.INotification.ITargetClient.ITextMessage event) {
        if (terminated) return;
        String msg = event.getTextMessage().getMessage();
        if (msg.startsWith(COMMAND_PREFIX)) {

            // Strip the command starter
            msg = msg.substring(COMMAND_PREFIX.length());

            synchronized (lock) {
                Iterator<Map.Entry<String, ICommandReceiver>> it = commands.entrySet().iterator();
                final List<ICommandReceiver> receivers = new ArrayList<>();
                final IQueryEvent.INotification.ITargetClient.ITextMessage fEvent = event;
                while (it.hasNext()) {
                    Map.Entry<String, ICommandReceiver> entry = it.next();

                    if (msg.startsWith(entry.getKey())) {
                        receivers.add(entry.getValue());
                    }
                }
                executorSvc.execute(() -> {
                    receivers.forEach(r -> r.receive(fEvent));
                });
            }
        }
    }

    @Override
    public void registerCommand(String command, ICommandReceiver receiver) {
        if (receiver == null)
            throw new IllegalArgumentException("CommandReceiver may not be null!");
        synchronized (lock) {
            commands.put(command, receiver);
        }
    }

    @Override
    public void unregisterCommand(String command, ICommandReceiver receiver) {
        synchronized (lock) {
            ICommandReceiver storedReceiver = commands.get(command);
            if (storedReceiver != null && (receiver == null || receiver == storedReceiver)) {
                commands.remove(command);
            }
        }
    }

    public void shutdown() {
        synchronized (lock) {
            terminated = true;
            boolean terminated_successfully = false;
            try {
                executorSvc.shutdownNow();
                terminated_successfully = executorSvc.awaitTermination(AWAIT_TERMINATION_DELAY, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                log.severe("Got interrupted while awaiting thread termination!", e);
            }
            if (!terminated_successfully) {
                log.warning("Some command receivers did not terminate gracefully! Either consider increasing the wait timeout or debug what plugin delays the shutdown!");
                log.warning("Be aware that the JVM will not exit until ALL threads have terminated!");
            }
        }
    }
}