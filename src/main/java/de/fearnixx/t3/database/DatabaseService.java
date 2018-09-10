package de.fearnixx.t3.database;

import de.fearnixx.t3.event.bot.IBotStateEvent;
import de.fearnixx.t3.plugin.persistent.PluginManager;
import de.fearnixx.t3.reflect.Inject;
import de.fearnixx.t3.reflect.Listener;
import de.mlessmann.logging.ILogReceiver;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.reflections.Reflections;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by MarkL4YG on 09-Feb-18
 */
public class DatabaseService {

    private static final Object CLASS_LOCK =  new Object();
    private static final List<Class<?>> ENTITIES = new CopyOnWriteArrayList<>();

    private static final String PROPERTIES_DEFAULT_CONTENT =
            "#Uncomment and fill out the following properties to enable the data source.\n"
            + "!hibernate.connection.url=\"jdbc:mysql://myhost:myport/mydatabase\"\n"
            + "!hibernate.connection.username=\"myuser\"\n"
            + "!hibernate.connection.password=\"mypass\"\n";

    @Inject
    public ILogReceiver logger;

    @Inject
    public PluginManager pluginManager;

    private File dbDir;

    private ClassLoader entityClassLoader;
    private BootstrapServiceRegistry baseRegistry;
    private Map<String, PersistenceUnitRep> persistenceUnits;

    public DatabaseService(File dbDir) {
        this.dbDir = dbDir;
        persistenceUnits = new ConcurrentHashMap<>();
    }

    private List<File> getPropertyFiles() {
        List<File> list = new ArrayList<>();
        if (dbDir.isDirectory()) {
            File[] files = dbDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.canRead() && file.getName().endsWith(".properties")) {
                        list.add(file);
                    }
                }
            }
        }
        return list;
    }

    public void onLoad() {
        List<File> dataSourceFiles = getPropertyFiles();

        if (!dataSourceFiles.isEmpty()) {
            this.entityClassLoader = pluginManager.getPluginClassLoader();
            checkClasses();

            BootstrapServiceRegistryBuilder baseRegistryBuilder = new BootstrapServiceRegistryBuilder();
            baseRegistryBuilder.applyClassLoader(entityClassLoader);
            this.baseRegistry = baseRegistryBuilder.build();

            for (File dataSourceFile : dataSourceFiles) {
                String name = dataSourceFile.getName().substring(0, dataSourceFile.getName().length() - 11);
                logger.fine("Trying to construct persistence unit: " + name);

                try {
                    Properties dataSourceProps = new Properties();
                    dataSourceProps.load(new FileInputStream(dataSourceFile));
                    boolean valid = dataSourceProps.containsKey("hibernate.connection.url")
                            && dataSourceProps.containsKey("hibernate.connection.username")
                            && dataSourceProps.containsKey("hibernate.connection.password");

                    if (valid) {
                        logger.info("Constructing persistence unit: ", name);

                        StandardServiceRegistryBuilder registryBuilder = new StandardServiceRegistryBuilder(baseRegistry);
                        applyDefaults(registryBuilder);
                        dataSourceProps.forEach((k, v) -> registryBuilder.applySetting((String) k, v));
                        persistenceUnits.put(name, new PersistenceUnitRep(registryBuilder.build(), getClasses()));
                    } else {
                        logger.warning("Cannot construct persistence unit: " + name + "! Make sure to set url, username and password.");
                    }
                } catch (IOException e) {

                }
            }
        }
    }

    private void applyDefaults(StandardServiceRegistryBuilder registryBuilder) {
        registryBuilder.applySetting("hibernate.format_sql", "true");
        registryBuilder.applySetting("hibernate.connection.driver_class", "com.mysql.jdbc.Driver");
        registryBuilder.applySetting("hibernate.connection.pool_size", "1");
        registryBuilder.applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        registryBuilder.applySetting("hibernate.c3p0.min_size", "1");
        registryBuilder.applySetting("hibernate.c3p0.max_size", "20");
        registryBuilder.applySetting("hibernate.c3p0.timeout", "300");
        registryBuilder.applySetting("hibernate.c3p0.max_statements", 50);
        registryBuilder.applySetting("hibernate.c3p0.idle_test_period", 3000);
    }

    private void checkClasses() {
        synchronized (CLASS_LOCK) {
            if (ENTITIES.isEmpty()) {
                logger.fine("Searching Entities.");

                Reflections reflect = pluginManager.getPluginScanner(entityClassLoader);
                Set<Class<?>> types = reflect.getTypesAnnotatedWith(Entity.class);
                types.forEach(entityType -> {
                    logger.fine("Found: " + entityType.getName());
                    ENTITIES.add(entityType);
                });
            }
        }
    }

    private Set<Class<?>> getClasses() {
        synchronized (CLASS_LOCK) {
            return new HashSet<>(ENTITIES);
        }
    }

    @Listener(order = Listener.Orders.LATEST)
    public void onShutdown(IBotStateEvent.IPostShutdown event) {
        persistenceUnits.forEach((k, u) -> u.close());
        persistenceUnits.clear();
    }

    public Optional<EntityManager> getEntityManager(String unitName) {
        PersistenceUnitRep rep = persistenceUnits.getOrDefault(unitName, null);
        if (rep != null) {
            return Optional.of(rep.getEntityManager());
        }

        File dataSourceFile = new File(dbDir, unitName + ".properties");
        if (!dataSourceFile.exists()) {
            try (FileWriter out = new FileWriter(dataSourceFile)) {
                out.write(PROPERTIES_DEFAULT_CONTENT);
                out.flush();
                logger.info("DataSource \"" + unitName + "\" requested but not available. Created template file for you.");
            } catch (IOException e) {
                logger.info("Cannot pre-create the datasource file. You will have to create it yourself.", e);
            }
        }
        return Optional.empty();
    }
}
