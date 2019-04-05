package de.fearnixx.jeak.teamspeak.data;

import de.fearnixx.jeak.Main;
import de.fearnixx.jeak.teamspeak.PropertyKeys;
import de.fearnixx.jeak.teamspeak.PropertyKeys.Channel;
import de.fearnixx.jeak.teamspeak.QueryCommands;
import de.fearnixx.jeak.teamspeak.TargetType;
import de.fearnixx.jeak.teamspeak.query.IQueryRequest;
import de.fearnixx.jeak.teamspeak.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

/**
 * Created by MarkL4YG on 15.06.17.
 */
public class TS3Channel extends TS3ChannelHolder {

    private static final Boolean SKIP_CHANNEL_MSG_WARNING = Main.getProperty("jeak.checks.channelMsg", false);

    public static final Logger logger = LoggerFactory.getLogger(TS3Channel.class);

    @Override
    public IQueryRequest sendMessage(String message) {
        if (!SKIP_CHANNEL_MSG_WARNING) {
            logger.warn("Sending commands to channels is not supported at the moment! You will see the message only in the current channel");
        }
        return IQueryRequest.builder()
                .command(QueryCommands.TEXTMESSAGE_SEND)
                .addKey(PropertyKeys.TextMessage.TARGET_ID, this.getID())
                .addKey(PropertyKeys.TextMessage.TARGET_TYPE, TargetType.CHANNEL)
                .addKey(PropertyKeys.TextMessage.MESSAGE, message)
                .build();
    }

    @Override
    public IQueryRequest delete() {
        return IQueryRequest.builder()
                .command(QueryCommands.CHANNEL.CHANNEL_DELETE)
                .addKey(Channel.ID, this.getID())
                .build();
    }

    @Override
    public IQueryRequest rename(String channelName) {
        return edit(Collections.singletonMap(Channel.NAME, channelName));
    }

    @Override
    public IQueryRequest moveBelow(Integer channelAboveId) {
        return edit(Collections.singletonMap(Channel.ORDER, channelAboveId.toString()));
    }

    @Override
    public IQueryRequest moveInto(Integer channelParentId) {
        return edit(Collections.singletonMap(Channel.PARENT, channelParentId.toString()));
    }

    @Override
    public IQueryRequest edit(Map<String, String> properties) {
        QueryBuilder queryBuilder = IQueryRequest.builder()
                .command(QueryCommands.CHANNEL.CHANNEL_EDIT)
                .addKey(Channel.ID, this.getID());

        properties.forEach(queryBuilder::addKey);
        return queryBuilder.build();
    }
}