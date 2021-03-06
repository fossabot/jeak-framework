package de.fearnixx.jeak.service.command;

public interface ICommandReceiver {

    /**
     * Receive a clients TextMessage. Can come from the following chat types:
     * * Private text
     * * Channel text
     * * Server text
     *
     * Commands must start with "!" in order to be received.
     * CommandReceivers only receive the commands they are registered for.
     *
     * @param ctx The context of the executed command
     */
    void receive(ICommandContext ctx) throws CommandException;
}
