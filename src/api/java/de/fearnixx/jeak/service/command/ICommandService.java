package de.fearnixx.jeak.service.command;

/**
 * Manages commands received by other clients over the query connection
 */
public interface ICommandService {

    /**
     * Register a command
     * When a command is already registered its old receiver is replaced!
     *
     * @param command The command to register to
     * @param receiver The receiver that wants to be triggered by that command
     */
    void registerCommand(String command, ICommandReceiver receiver);

    /**
     * Unregister a command
     * When a receiver is provided the command will only be removed if the receiver is the same!
     *
     * @param command The command
     * @param receiver null or the receiver to unregister
     */
    void unregisterCommand(String command, ICommandReceiver receiver);
}
