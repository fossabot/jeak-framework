package de.fearnixx.jeak;

import de.fearnixx.jeak.event.EventService;
import de.fearnixx.jeak.event.bot.BotStateEvent;
import de.fearnixx.jeak.event.bot.IBotStateEvent;
import de.fearnixx.jeak.plugin.PluginContainer;
import de.fearnixx.jeak.plugin.persistent.PluginManager;
import de.fearnixx.jeak.plugin.persistent.PluginRegistry;
import de.fearnixx.jeak.profile.IProfileService;
import de.fearnixx.jeak.reflect.*;
import de.fearnixx.jeak.service.IServiceManager;
import de.fearnixx.jeak.service.ServiceManager;
import de.fearnixx.jeak.service.command.CommandService;
import de.fearnixx.jeak.service.command.ICommandService;
import de.fearnixx.jeak.service.database.DatabaseService;
import de.fearnixx.jeak.service.event.IEventService;
import de.fearnixx.jeak.service.notification.NotificationService;
import de.fearnixx.jeak.service.notification.INotificationService;
import de.fearnixx.jeak.service.locale.ILocalizationService;
import de.fearnixx.jeak.service.locale.LocalizationService;
import de.fearnixx.jeak.service.mail.IMailService;
import de.fearnixx.jeak.service.mail.MailService;
import de.fearnixx.jeak.service.permission.base.IPermissionService;
import de.fearnixx.jeak.service.permission.base.PermissionService;
import de.fearnixx.jeak.service.permission.teamspeak.ITS3PermissionProvider;
import de.fearnixx.jeak.service.permission.teamspeak.TS3PermissionProvider;
import de.fearnixx.jeak.service.profile.ProfileService;
import de.fearnixx.jeak.service.task.ITaskService;
import de.fearnixx.jeak.task.TaskService;
import de.fearnixx.jeak.teamspeak.IServer;
import de.fearnixx.jeak.teamspeak.Server;
import de.fearnixx.jeak.teamspeak.TS3ConnectionTask;
import de.fearnixx.jeak.teamspeak.cache.DataCache;
import de.fearnixx.jeak.teamspeak.cache.IDataCache;
import de.mlessmann.confort.api.IConfig;
import de.mlessmann.confort.api.IConfigNode;
import de.mlessmann.confort.api.except.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Created by Life4YourGames on 22.05.17.
 */
public class JeakBot implements Runnable, IBot {

    // * * * STATICS  * * * //
    public static final Charset CHAR_ENCODING = Charset.forName("UTF-8");
    public static final String VERSION = "@VERSION@";

    private static final Logger logger = LoggerFactory.getLogger(JeakBot.class);

    // * * * VOLATILES * * * //

    private volatile boolean initCalled = false;

    // * * * FIELDS * * * //

    private Consumer<JeakBot> onShutdown;
    private UUID instanceUUID = UUID.randomUUID();

    private File baseDir;
    private File confDir;

    private IConfig configRep;
    private IConfigNode config;

    private PluginManager pMgr;
    private InjectionService injectionService;
    private Map<String, PluginContainer> plugins;

    private TS3ConnectionTask connectionTask = new TS3ConnectionTask();
    private Server server;
    private DataCache dataCache;

    private EventService eventService;
    private TaskService taskService;
    private CommandService commandService;
    private NotificationService notificationService;

    private ExecutorService shutdownExecutor = Executors.newSingleThreadExecutor();

    // * * * CONSTRUCTION * * * //

    public void setConfig(IConfig configRep) {
        if (this.configRep != null)
            throw new IllegalStateException("Cannot change config once set!");
        this.configRep = configRep;
    }

    public void setPluginManager(PluginManager pMgr) {
        if (this.pMgr != null)
            throw new IllegalStateException("Cannot change pluginManager once set!");
        this.pMgr = pMgr;
    }

    // * * * [Runnable] * * * //

    @Override
    public void run() {
        logger.info("Initializing JeakBot version {}", VERSION);

        if (initCalled) {
            throw new IllegalStateException("Reinitialization of JeakBot instances is not supported! Completely shut down beforehand and/or create a new one.");
        }

        // Bot Pre-Initialization
        initCalled = true;
        plugins = new HashMap<>();

        // Create services and register them
        logger.debug("Constructing services");
        ServiceManager serviceManager = new ServiceManager();
        PermissionService permissionService = new PermissionService();
        TS3PermissionProvider ts3permissionProvider = new TS3PermissionProvider();
        DatabaseService databaseService = new DatabaseService(new File(confDir, "databases"));
        notificationService = new NotificationService();
        MailService mailService = new MailService(new File(confDir, "mail"));
        LocalizationService localizationService = new LocalizationService();
        ProfileService profileService = new ProfileService(new File(confDir, "profiles"));

        eventService = new EventService();
        taskService = new TaskService((pMgr.estimateCount() > 0 ? pMgr.estimateCount() : 10) * 10);
        commandService = new CommandService();

        injectionService = new InjectionService(new InjectionContext(serviceManager, "frw"));
        injectionService.addProvider(new ConfigProvider(confDir));
        injectionService.addProvider(new DataSourceProvider());
        injectionService.addProvider(new TransportProvider());
        injectionService.addProvider(new LocalizationProvider(localizationService));
        server = new Server();
        dataCache = new DataCache(eventService);

        serviceManager.registerService(PluginManager.class, pMgr);
        serviceManager.registerService(IBot.class, this);
        serviceManager.registerService(IServiceManager.class, serviceManager);
        serviceManager.registerService(IEventService.class, eventService);
        serviceManager.registerService(ITaskService.class, taskService);
        serviceManager.registerService(ILocalizationService.class, localizationService);
        serviceManager.registerService(ICommandService.class, commandService);
        serviceManager.registerService(IInjectionService.class, injectionService);
        serviceManager.registerService(IServer.class, server);
        serviceManager.registerService(IDataCache.class, dataCache);
        serviceManager.registerService(IPermissionService.class, permissionService);
        serviceManager.registerService(ITS3PermissionProvider.class, ts3permissionProvider);
        serviceManager.registerService(DatabaseService.class, databaseService);
        serviceManager.registerService(INotificationService.class, notificationService);
        serviceManager.registerService(IMailService.class, mailService);
        serviceManager.registerService(IProfileService.class, profileService);

        injectionService.injectInto(serviceManager);
        injectionService.injectInto(eventService);
        injectionService.injectInto(taskService);
        injectionService.injectInto(localizationService);
        injectionService.injectInto(commandService);
        injectionService.injectInto(server);
        injectionService.injectInto(connectionTask);
        injectionService.injectInto(permissionService);
        injectionService.injectInto(ts3permissionProvider);
        injectionService.injectInto(databaseService);
        injectionService.injectInto(dataCache);
        injectionService.injectInto(mailService);
        injectionService.injectInto(profileService);
        injectionManager.injectInto(notificationService);

        taskService.start();
        eventService.registerListener(connectionTask);
        eventService.registerListeners(localizationService);
        eventService.registerListeners(commandService);
        eventService.registerListener(dataCache);
        eventService.registerListener(mailService);
        eventService.registerListener(profileService);

        pMgr.setIncludeCP(true);
        pMgr.load();
        databaseService.onLoad();
        mailService.onLoad();

        Map<String, PluginRegistry> regMap = pMgr.getAllPlugins();
        // Load all plugins - This is where dependencies are being enforced
        regMap.forEach((n, pr) -> loadPlugin(regMap, n, pr));
        StringBuilder b = new StringBuilder();
        plugins.forEach((k, v) -> b.append(k).append(", "));
        logger.info("Loaded {} plugin(s): {}", plugins.size(), b);
        eventService.fireEvent(new BotStateEvent.PluginsLoaded().setBot(this));
        eventService.fireEvent(new BotStateEvent.PreInitializeEvent().setBot(this));

        // Initialize Bot configuration and Plugins
        BotStateEvent.Initialize event = new BotStateEvent.Initialize();
        event.setBot(this);
        initializeConfiguration(event);
        if (event.isCanceled()) {
            shutdown();
            return;
        }

        eventService.fireEvent(event);
        if (event.isCanceled()) {
            logger.warn("An initialization task has requested the bot to cancel startup. Doing that.");
            shutdown();
            return;
        }

        String host = config.getNode("host").asString();
        Integer port = config.getNode("port").asInteger();
        String user = config.getNode("user").asString();
        String pass = config.getNode("pass").asString();
        Integer ts3InstID = config.getNode("instance").asInteger();
        Boolean useSSL = config.getNode("ssl").optBoolean(false);
        String nickName = config.getNode("nick").optString("JeakBot");
        server.setCredentials(host, port, user, pass, ts3InstID, useSSL, nickName);

        Boolean doNetDump = Main.getProperty("bot.connection.netdump", Boolean.FALSE);
        if (doNetDump) {
            logger.info("Dedicated net-dumping is currently not implemented. The option will have no effect atm.");
        }

        taskService.runTask(connectionTask);
    }

    private boolean loadPlugin(Map<String, PluginRegistry> reg, String id, PluginRegistry pr) {
        logger.info("Loading plugin {}", id);
        PluginContainer c = plugins.getOrDefault(id, null);

        if (c == null) c = pr.newContainer();
        plugins.put(id, c);

        if (c.getState() == PluginContainer.State.DEPENDENCIES) {
            logger.error("Possible circular dependency detected! Plugin {} was resolving dependencies when requested!", id);
            return true;
        } else if (c.getState() == PluginContainer.State.DONE) {
            logger.error("Plugin already initialized - Skipping");
            return true;
        } else if (c.getState() != PluginContainer.State.INIT) {
            throw new IllegalStateException(new ConcurrentModificationException("Attempt to load plugin in invalid state!"));
        }

        c.setState(PluginContainer.State.DEPENDENCIES);

        for (String dep : pr.getHARD_depends()) {
            if (!reg.containsKey(dep) || !loadPlugin(reg, dep, reg.get(dep))) {
                logger.error("Unresolvable HARD dependency: {} for plugin {}", dep, id);
                c.setState(PluginContainer.State.FAILED_DEP);
                return false;
            }
        }

        c.setState(PluginContainer.State.INJECT);

        try {
            c.construct();
        } catch (Exception e) {
            logger.error("Failed to construct plugin: ", id, e);
            c.setState(PluginContainer.State.FAILED);
            return false;
        }

        injectionService.injectInto(c.getPlugin());
        eventService.registerListener(c.getPlugin());
        c.setState(PluginContainer.State.DONE);
        logger.debug("Initialized plugin {}", id);
        return true;
    }

    // * * * Configuration * * * //

    /**
     * Initializes the bots configuration
     * Makes use of the {@link IBotStateEvent.IInitializeEvent} in order to cancel startup on unsuccessful init.
     */
    protected void initializeConfiguration(IBotStateEvent.IInitializeEvent event) {
        // Construct configLoader but only directly read from the file when it exists
        // Otherwise cancel startup and create default config

        try {
            configRep.load();

        } catch (FileNotFoundException e) {
            logger.warn("Creating new default configuration. Requesting shutdown after initialization.");
            configRep.createRoot();
            event.cancel();

        } catch (IOException | ParseException e) {
            logger.error("Failed to load configuration!", e);
            event.cancel();
            return;
        }
        config = configRep.getRoot();

        boolean rewrite;

        rewrite = config.getNode("host").defaultValue("localhost");
        rewrite = rewrite | config.getNode("port").defaultValue(10011);
        rewrite = rewrite | config.getNode("user").defaultValue("serveradmin");
        rewrite = rewrite | config.getNode("pass").defaultValue("password");
        rewrite = rewrite | config.getNode("instance").defaultValue(1);
        rewrite = rewrite | config.getNode("nick").defaultValue("JeakBot");

        if (rewrite) {
            if (!saveConfig()) {
                logger.error("Failed to rewrite configuration. Aborting startup, just in case.");
                event.cancel();
            }
            logger.warn("One or more settings have been set to default values. Please review the configuration.");
            event.cancel();
        }
    }

    public boolean saveConfig() {
        try {
            configRep.save();
            return true;
        } catch (IOException e) {
            logger.error("Failed to save configuration!", e);
            return false;
        }
    }

    public void setBaseDir(File baseDir) {
        this.baseDir = baseDir;
        logger.debug("Base directory changed to: {}", baseDir);
    }

    public void setConfDir(File confDir) {
        this.confDir = confDir;
        logger.debug("Conf directory changed to: {}", confDir);
    }

    // * * * MISC * * * //

    @Override
    public File getBaseDirectory() {
        return baseDir;
    }

    @Override
    public File getConfigDirectory() {
        return confDir;
    }

    @Override
    public IServer getServer() {
        return server;
    }

    @Override
    public IDataCache getDataCache() {
        return dataCache;
    }


    // * * * RUNTIME * * * //

    public void shutdown() {

        // Decouple the shutdown callback from threads running inside the bots context.
        // This avoids any termination interrupts going on inside the framework instance from interrupting our shutdown handler.
        shutdownExecutor.execute(() -> {
            final LinkedList<ExecutorService> executors = new LinkedList<>();
            final BotStateEvent.PreShutdown preShutdown = new BotStateEvent.PreShutdown();
            preShutdown.setBot(this);
            eventService.fireEvent(preShutdown);
            executors.addAll(preShutdown.getExecutors());

            saveConfig();

            final BotStateEvent.PostShutdown postShutdown = new BotStateEvent.PostShutdown();
            postShutdown.setBot(this);
            eventService.fireEvent(postShutdown);
            executors.addAll(postShutdown.getExecutors());


            executors.removeIf(ExecutorService::isShutdown);
            if (!executors.isEmpty()) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    // ignore interruption.
                }
                executors.forEach(ExecutorService::shutdownNow);
            }

            eventService.shutdown();

            if (onShutdown != null) {
                onShutdown.accept(this);
            }
        });
    }

    public void onShutdown(Consumer<JeakBot> onShutdown) {
        this.onShutdown = onShutdown;
    }

    @Override
    public UUID getInstanceUUID() {
        return instanceUUID;
    }
}
