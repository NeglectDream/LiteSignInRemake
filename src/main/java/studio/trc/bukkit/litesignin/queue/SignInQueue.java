package studio.trc.bukkit.litesignin.queue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import studio.trc.bukkit.litesignin.database.DatabaseTable;
import studio.trc.bukkit.litesignin.util.SignInDate;
import studio.trc.bukkit.litesignin.database.engine.SQLQuery;
import studio.trc.bukkit.litesignin.database.engine.SQLiteEngine;
import studio.trc.bukkit.litesignin.util.PluginControl;

/**
 * Sign-in ranking.
 * <p>
 * Reads ranking data directly from the SQLite database; the in-memory list is
 * refreshed on demand according to {@code SQLite-Storage.Refresh-Interval}.
 * @author Dean
 */
public class SignInQueue
    extends ArrayList<SignInQueueElement>
{
    private static final Map<SignInDate, SignInQueue> cache = new HashMap();
    private static final Map<SignInDate, Long> lastUpdateTime = new HashMap();

    public static SignInQueue getInstance() {
        SignInDate date = SignInDate.getInstance(new Date());
        for (SignInDate dates : cache.keySet()) {
            if (dates.equals(date)) {
                SignInQueue queue = cache.get(dates);
                queue.checkUpdate();
                return queue;
            }
        }
        SignInQueue queue = new SignInQueue(date);
        cache.put(date, queue);
        queue.checkUpdate();
        return queue;
    }

    public static SignInQueue getInstance(SignInDate date) {
        for (SignInDate dates : cache.keySet()) {
            if (dates.equals(date)) {
                SignInQueue queue = cache.get(dates);
                queue.checkUpdate();
                return queue;
            }
        }
        SignInQueue queue = new SignInQueue(date);
        cache.put(date, queue);
        queue.checkUpdate();
        return queue;
    }

    private final SignInDate date;

    public SignInQueue(SignInDate date) {
        this.date = date;
    }

    public SignInDate getDate() {
        return date;
    }

    public void loadQueue() {
        if (!PluginControl.enableSignInRanking()) {
            return;
        }
        try {
            SQLiteEngine sqlite = SQLiteEngine.getInstance();
            sqlite.checkConnection();
            try (SQLQuery query = sqlite.executeQuery("SELECT * FROM " + sqlite.getTableSyntax(DatabaseTable.PLAYER_DATA) + " WHERE History LIKE '%" + date.getDataText(false) + "%'")) {
                ResultSet rs = query.getResult();
                clear();
                while (rs.next()) {
                    try {
                        UUID uuid = UUID.fromString(rs.getString("UUID"));
                        SignInDate time = null;
                        if (date.equals(SignInDate.getInstance(new Date()))) {
                            time = SignInDate.getInstance(date.getYear(), date.getMonth(), date.getDay(), rs.getInt("Hour"), rs.getInt("Minute"), rs.getInt("Second"));
                        } else {
                            Integer hour = null, minute = null, second = null;
                            for (String data : Arrays.asList(rs.getString("History").split(", "))) {
                                SignInDate targetDate = SignInDate.getInstance(data);
                                if (date.equals(targetDate)) {
                                    if (targetDate.hasTimePeriod()) {
                                        hour = targetDate.getHour();
                                        minute = targetDate.getMinute();
                                        second = targetDate.getSecond();
                                    }
                                    if (hour != null && minute != null && second != null) {
                                        time = SignInDate.getInstance(date.getYear(), date.getMonth(), date.getDay(), hour, minute, second);
                                    } else {
                                        time = SignInDate.getInstance(date.getYear(), date.getMonth(), date.getDay());
                                    }
                                    break;
                                }
                            }
                        }
                        if (time != null) add(new SignInQueueElement(uuid, time, rs.getString("Name")));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
            lastUpdateTime.put(date, System.currentTimeMillis());
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public SignInQueueElement getElement(UUID uuid) {
        checkUpdate();
        for (SignInQueueElement element : new ArrayList<>(this)) {
            UUID queueUUID = element.getUUID();
            if (queueUUID.equals(uuid)) {
                return element;
            }
        }
        return null;
    }

    public int getRank(UUID uuid) {
        if (!PluginControl.enableSignInRanking()) {
            return 0;
        }
        checkUpdate();
        SignInQueueElement user = getElement(uuid);
        if (user == null) {
            return 0;
        }
        int rank = 1;
        if (user.getSignInDate().hasTimePeriod()) {
            for (SignInQueueElement element : this) {
                if (user.getSignInDate().compareTo(element.getSignInDate()) > 0 && element.getSignInDate().hasTimePeriod()) {
                    rank++;
                }
            }
        } else {
            for (SignInQueueElement element : this) {
                if (element.getSignInDate().hasTimePeriod()) {
                    rank++;
                }
            }
            for (SignInQueueElement element : getUnknownTimesElement()) {
                if (!element.getUUID().equals(uuid)) {
                    rank++;
                } else {
                    return rank;
                }
            }
        }
        return rank;
    }

    public List<SignInQueueElement> getUnknownTimesElement() {
        List<SignInQueueElement> list = new ArrayList<>();
        for (SignInQueueElement element : new ArrayList<>(this)) {
            if (!element.getSignInDate().hasTimePeriod()) {
                list.add(element);
            }
        }
        return list;
    }

    public void checkUpdate() {
        if (!PluginControl.enableSignInRanking()) {
            return;
        }
        if (!lastUpdateTime.containsKey(date)) {
            loadQueue();
        }
        if (PluginControl.getSQLiteRefreshInterval() == 0 || System.currentTimeMillis() - lastUpdateTime.get(date) >= PluginControl.getSQLiteRefreshInterval() * 1000) {
            loadQueue();
        }
    }

    public List<SignInQueueElement> getRankingUser(int ranking) {
        checkUpdate();
        List<SignInQueueElement> result = new ArrayList<>();
        for (SignInQueueElement element : new ArrayList<>(this)) {
            if (getRank(element.getUUID()) == ranking) {
                result.add(element);
            }
        }
        return result;
    }
}
