/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.common.commands.log;

import me.lucko.luckperms.common.actionlog.ExtendedLogEntry;
import me.lucko.luckperms.common.actionlog.Log;
import me.lucko.luckperms.common.command.CommandResult;
import me.lucko.luckperms.common.command.abstraction.SubCommand;
import me.lucko.luckperms.common.command.access.CommandPermission;
import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.locale.LocaleManager;
import me.lucko.luckperms.common.locale.command.CommandSpec;
import me.lucko.luckperms.common.locale.message.Message;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.sender.Sender;
import me.lucko.luckperms.common.storage.DataConstraints;
import me.lucko.luckperms.common.utils.DurationFormatter;
import me.lucko.luckperms.common.utils.Predicates;
import me.lucko.luckperms.common.utils.Uuids;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.UUID;

public class LogUserHistory extends SubCommand<Log> {
    private static final int ENTRIES_PER_PAGE = 10;

    public LogUserHistory(LocaleManager locale) {
        super(CommandSpec.LOG_USER_HISTORY.localize(locale), "userhistory", CommandPermission.LOG_USER_HISTORY, Predicates.notInRange(1, 2));
    }

    @Override
    public CommandResult execute(LuckPermsPlugin plugin, Sender sender, Log log, List<String> args, String label) {
        String target = args.get(0);
        int page = Integer.MIN_VALUE;

        if (args.size() == 2) {
            try {
                page = Integer.parseInt(args.get(1));
            } catch (NumberFormatException e) {
                // invalid page
                return showLog(-1, null, sender, log);
            }
        }

        UUID uuid = Uuids.parseNullable(target);
        if (uuid == null) {
            if (!plugin.getConfiguration().get(ConfigKeys.ALLOW_INVALID_USERNAMES)) {
                if (!DataConstraints.PLAYER_USERNAME_TEST.test(target)) {
                    Message.USER_INVALID_ENTRY.send(sender, target);
                    return CommandResult.INVALID_ARGS;
                }
            } else {
                if (!DataConstraints.PLAYER_USERNAME_TEST_LENIENT.test(target)) {
                    Message.USER_INVALID_ENTRY.send(sender, target);
                    return CommandResult.INVALID_ARGS;
                }
            }

            uuid = plugin.getStorage().getPlayerUuid(target.toLowerCase()).join();
            if (uuid == null) {
                if (!plugin.getConfiguration().get(ConfigKeys.USE_SERVER_UUID_CACHE)) {
                    Message.USER_NOT_FOUND.send(sender, target);
                    return CommandResult.INVALID_ARGS;
                }

                uuid = plugin.getBootstrap().lookupUuid(target).orElse(null);
                if (uuid == null) {
                    Message.USER_NOT_FOUND.send(sender, target);
                    return CommandResult.INVALID_ARGS;
                }
            }
        }

        if (page == Integer.MIN_VALUE) {
            page = log.getUserHistoryMaxPages(uuid, ENTRIES_PER_PAGE);
        }

        return showLog(page, uuid, sender, log);
    }

    private static CommandResult showLog(int page, UUID user, Sender sender, Log log) {
        int maxPage = log.getUserHistoryMaxPages(user, ENTRIES_PER_PAGE);
        if (maxPage == 0) {
            Message.LOG_NO_ENTRIES.send(sender);
            return CommandResult.STATE_ERROR;
        }

        if (page < 1 || page > maxPage) {
            Message.LOG_INVALID_PAGE_RANGE.send(sender, maxPage);
            return CommandResult.INVALID_ARGS;
        }

        SortedMap<Integer, ExtendedLogEntry> entries = log.getUserHistory(page, user, ENTRIES_PER_PAGE);
        String name = entries.values().stream().findAny().get().getActedName();
        Message.LOG_HISTORY_USER_HEADER.send(sender, name, page, maxPage);

        long now = System.currentTimeMillis() / 1000L;
        for (Map.Entry<Integer, ExtendedLogEntry> e : entries.entrySet()) {
            long time = e.getValue().getTimestamp();
            Message.LOG_ENTRY.send(sender,
                    e.getKey(),
                    DurationFormatter.CONCISE_LOW_ACCURACY.format(now - time),
                    e.getValue().getActorFriendlyString(),
                    Character.toString(e.getValue().getType().getCode()),
                    e.getValue().getActedFriendlyString(),
                    e.getValue().getAction()
            );
        }

        return CommandResult.SUCCESS;
    }
}
