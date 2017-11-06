package de.fearnixx.t3.event.state;

import de.fearnixx.t3.event.IEvent;

/**
 * Created by MarkL4YG on 09.06.17.
 */
public interface IBotStateEvent extends IEvent.IBotEvent {

    /**
     * ## Phase 1 ##
     * Plugins are now loaded
     * (Early-Initialization)
     */
    interface IPluginsLoaded extends IBotStateEvent {

    }

    /**
     * ## Phase 2 ##
     * The Bot completed early-initialization
     * Plugins now initialize
     */
    interface IInitializeEvent extends IBotStateEvent {

        /**
         * Cancels the Start-Up.
         * Keep in mind that the event execution is not interrupted!
         * This indicates a critical plugin ore service could not successfully or completely initialize
         * but does not require ungraceful shut-down.
         *
         * Suitable for example for first-time setup.
         */
        void cancel();

        /**
         * Whether or not something already invoked {@link #cancel()}
         */
        boolean isCanceled();
    }

    /**
     * ## Phase 3 ##
     * The Bot is about to establish the main query connection
     */
    interface IPreConnect extends IBotStateEvent {

    }

    /**
     * ## Phase 4 ##
     * The Bot has established the main query connection successfully
     * The following commands have been executed:
     * * use
     * * login
     * * clientupdate nickname
     */
    interface IPostConnect extends IBotStateEvent {

    }

    /**
     * ## Phase 5 ##
     * The Bot is about to shut down
     * The main connection is about to close
     */
    interface IPreShutdown extends IBotStateEvent {

    }

    /**
     * ## Phase 6 ##
     * Shutdown completed the bot (not necessarily the application!) is about to exit
     */
    interface IPostShutdown extends IBotStateEvent {

    }
}