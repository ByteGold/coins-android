package com.bubelov.coins.service.sync.merchants;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import com.bubelov.coins.R;
import com.bubelov.coins.dagger.Injector;
import com.bubelov.coins.database.DbContract;
import com.bubelov.coins.model.Merchant;
import com.bubelov.coins.model.MerchantNotification;
import com.bubelov.coins.model.NotificationArea;
import com.bubelov.coins.provider.NotificationAreaProvider;
import com.bubelov.coins.receiver.ClearMerchantNotificationsReceiver;
import com.bubelov.coins.ui.activity.MapActivity;
import com.bubelov.coins.util.DistanceUtils;

import java.util.List;
import java.util.UUID;

/**
 * Author: Igor Bubelov
 * Date: 12/07/14 14:46
 */

public class UserNotificationController {
    public static final String NEW_MERCHANT_NOTIFICATION_GROUP = "NEW_MERCHANT";

    private Context context;

    public UserNotificationController(Context context) {
        this.context = context;
    }

    public boolean shouldNotifyUser(Merchant merchant) {
        NotificationArea notificationArea = new NotificationAreaProvider(context).get();

        if (notificationArea == null) {
            return false;
        }

        if (DistanceUtils.getDistance(notificationArea.getCenter(), merchant.getPosition()) > notificationArea.getRadiusMeters()) {
            return false;
        }

        SQLiteDatabase db = Injector.INSTANCE.getAppComponent().database();

        Cursor cursor = db.query(DbContract.Merchants.TABLE_NAME,
                new String[] { DbContract.Merchants._ID },
                "_id = ?",
                new String[] { String.valueOf(merchant.getId()) },
                null,
                null,
                null);

        boolean alreadyExists = cursor.getCount() > 0;
        cursor.close();

        return !alreadyExists;

    }

    public void notifyUser(long merchantId, String merchantName) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_new_merchant))
                .setContentText(!TextUtils.isEmpty(merchantName) ? merchantName : context.getString(R.string.notification_new_merchant_no_name))
                .setDeleteIntent(prepareClearMerchantsIntent())
                .setAutoCancel(true)
                .setGroup(NEW_MERCHANT_NOTIFICATION_GROUP);

        Intent intent = MapActivity.newShowMerchantIntent(context, merchantId, true);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, UUID.randomUUID().hashCode(), intent, 0);
        builder.setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(UUID.randomUUID().hashCode(), builder.build());

        MerchantNotification merchantNotification = new MerchantNotification();
        merchantNotification.setMerchantId(merchantId);
        MerchantNotification.insert(merchantNotification);

        if (MerchantNotification.queryForAll().size() > 1) {
            issueGroupNotification(MerchantNotification.queryForAll());
        }
    }

    private void issueGroupNotification(List<MerchantNotification> pendingMerchants) {
        NotificationArea notificationArea = new NotificationAreaProvider(context).get();

        Intent intent = MapActivity.newShowNotificationAreaIntent(context, notificationArea, true);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, NEW_MERCHANT_NOTIFICATION_GROUP.hashCode(), intent, 0);

        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        style.setBigContentTitle(context.getString(R.string.notification_new_merchants_content_title, String.valueOf(pendingMerchants.size())));

        for (MerchantNotification notification : pendingMerchants) {
            Merchant merchant = Merchant.find(notification.getMerchantId());
            style.addLine(merchant.getName());
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setContentTitle(context.getString(R.string.notification_new_merchants_content_title, String.valueOf(pendingMerchants.size())))
                .setContentText(context.getString(R.string.notification_new_merchants_content_text))
                .setDeleteIntent(prepareClearMerchantsIntent())
                .setStyle(style)
                .setAutoCancel(true)
                .setGroup(NEW_MERCHANT_NOTIFICATION_GROUP)
                .setGroupSummary(true);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NEW_MERCHANT_NOTIFICATION_GROUP.hashCode(), builder.build());
    }

    private PendingIntent prepareClearMerchantsIntent() {
        Intent deleteIntent = new Intent(ClearMerchantNotificationsReceiver.ACTION);
        PendingIntent deletePendingIntent = PendingIntent.getBroadcast(context, 0, deleteIntent, 0);
        context.registerReceiver(new ClearMerchantNotificationsReceiver(), new IntentFilter(ClearMerchantNotificationsReceiver.ACTION));
        return deletePendingIntent;
    }
}