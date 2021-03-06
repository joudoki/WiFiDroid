package rlewelle.wifidroid;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Pair;
import android.view.*;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import rlewelle.wifidroid.data.AccessPoint;
import rlewelle.wifidroid.data.AccessPointDataPoint;

import java.util.*;

public class WifiListActivity extends ListActivity implements DataService.IDataServicable{
    private DataService.DataServiceLink serviceLink;
    private NetworkListAdapter adapter;

    // Indicates whether or not the serviceLink should be destroyed in onDestroy()
    // This will be false in the case of a configuration change (i.e. rotation)
    private boolean destroyServiceLink = true;

    // Comparator for ordering by signal strength (high -> low)
    private static final Comparator<Map.Entry<AccessPoint, Map.Entry<Long, AccessPointDataPoint>>> ORDER_BY_STRENGTH =
            new Comparator<Map.Entry<AccessPoint, Map.Entry<Long, AccessPointDataPoint>>>() {

        @Override
        public int compare(
                Map.Entry<AccessPoint, Map.Entry<Long, AccessPointDataPoint>> a,
                Map.Entry<AccessPoint, Map.Entry<Long, AccessPointDataPoint>> b) {

            return WifiManager.compareSignalLevel(
                    b.getValue().getValue().getLevel(),
                    a.getValue().getValue().getLevel()
            );
        }
    };

    // Comparator for ordering by name (low -> high)
    private static final Comparator<Map.Entry<AccessPoint, Map.Entry<Long, AccessPointDataPoint>>> ORDER_BY_NAME =
            new Comparator<Map.Entry<AccessPoint, Map.Entry<Long, AccessPointDataPoint>>>() {

        @Override
        public int compare(
                Map.Entry<AccessPoint, Map.Entry<Long, AccessPointDataPoint>> a,
                Map.Entry<AccessPoint, Map.Entry<Long, AccessPointDataPoint>> b) {

            return a.getKey().getSSID().compareTo(b.getKey().getSSID());
        }
    };

    private Comparator<Map.Entry<AccessPoint, Map.Entry<Long, AccessPointDataPoint>>> comparator = ORDER_BY_NAME;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        serviceLink = (DataService.DataServiceLink) getLastNonConfigurationInstance();

        if (serviceLink == null) {
            serviceLink = new DataService.DataServiceLink(this);
            serviceLink.onCreate();
        }

        adapter = new NetworkListAdapter();
        setListAdapter(adapter);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        super.onRetainNonConfigurationInstance();

        destroyServiceLink = false;
        return serviceLink;
    }

    @Override
    protected void onDestroy() {
        if (destroyServiceLink)
            serviceLink.onDestroy();

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // We may not have received the latest updates (via intent) while paused,
        // so just ask the DataService for the most recent updates
        if (serviceLink.getService() == null)
            return;

        displayLatestResults();
    }

    @Override
    public void onServiceConnected() {}

    @Override
    public void onServiceDisconnected() {}

    @Override
    public void onScanResultsReceived() {
        displayLatestResults();
    }

    @Override
    public Context getContext() {
        return getApplicationContext();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(this).inflate(R.menu.main, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.wifi_list_channels:
                Intent intent = new Intent(this, WifiChannelsActivity.class);
                startActivity(intent);
                break;

            case R.id.wifi_list_auto_refresh:
                final String[] options = new String[] {"0", "200", "500", "1000", "2000", "3000", "5000", "10000"};

                Dialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Automatic Update")
                    //.setMessage("Select the number of milliseconds to wait between updates (or 0 to get them ASAP)")
                    .setSingleChoiceItems(
                         options,
                         -1,
                         new DialogInterface.OnClickListener() {
                             @Override
                             public void onClick(DialogInterface dialogInterface, int which) {
                                 long delay = Long.parseLong(options[which]);
                                 WifiListActivity.this.serviceLink.getService().setUpdateDelay(delay);
                                 dialogInterface.dismiss();
                             }
                         }
                    )
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                        }
                    })
                    .create();

                dialog.show();

                break;

            case R.id.wifi_list_refresh:
                serviceLink.getService().requestUpdate();
                break;

            case R.id.wifi_list_sort_name:
                comparator = ORDER_BY_NAME;
                adapter.sort(comparator);
                break;

            case R.id.wifi_list_sort_strength:
                comparator = ORDER_BY_STRENGTH;
                adapter.sort(comparator);
                break;

            case R.id.wifi_list_reset:
                serviceLink.getService().clearData();
                adapter.clear();
                break;

            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    @Override
    protected void onListItemClick(ListView list, View view, int position, long id) {
        AccessPoint ap = ((Map.Entry<AccessPoint, Pair<Long, AccessPointDataPoint>>) getListView().getItemAtPosition(position)).getKey();

        Intent intent = new Intent(this, WifiMeterActivity.class);
        intent.putExtra(WifiMeterActivity.EXTRA_AP, ap);
        startActivity(intent);
    }

    private void displayLatestResults() {
        if (serviceLink.getService() == null)
            return;

        // Retrieve and flatten result set into a list for use with the adapter
        Map<AccessPoint, Map.Entry<Long, AccessPointDataPoint>> results = serviceLink.getService().getAggregatedResults();

        // Flatten the map down to a list suitable for use with an array adapter
        List<Map.Entry<AccessPoint, Map.Entry<Long, AccessPointDataPoint>>> data = new ArrayList<>(results.entrySet());

        adapter.update(data, serviceLink.getService().getLastUpdateTimeInMillis());
        adapter.sort(comparator);
    }

    public class NetworkListAdapter extends ArrayAdapter<Map.Entry<AccessPoint, Map.Entry<Long, AccessPointDataPoint>>> {
        private List<Map.Entry<AccessPoint, Map.Entry<Long, AccessPointDataPoint>>> data;
        private long updateTime;

        public NetworkListAdapter() {
            super(WifiListActivity.this, R.layout.wifi_list_row, R.id.network_ssid);
        }

        public void update(List<Map.Entry<AccessPoint, Map.Entry<Long, AccessPointDataPoint>>> data, long updateTime) {
            this.updateTime = updateTime;

            clear();
            addAll(data);
            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            NetworkListRowHolder holder;

            if (convertView == null || convertView.getTag() == null) {
                convertView = getLayoutInflater().inflate(R.layout.wifi_list_row, null);
                holder = new NetworkListRowHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (NetworkListRowHolder)convertView.getTag();
            }

            Map.Entry<AccessPoint, Map.Entry<Long, AccessPointDataPoint>> entry = getItem(position);
            holder.hydrate(entry.getKey(), entry.getValue().getKey(), entry.getValue().getValue());

            return convertView;
        }

        public class NetworkListRowHolder {
            TextView ssid;
            TextView seen;
            TextView channel;
            ProgressBar strength;

            public NetworkListRowHolder(View view) {
                ssid = (TextView)view.findViewById(R.id.network_ssid);
                seen = (TextView)view.findViewById(R.id.network_lastseen);
                channel = (TextView)view.findViewById(R.id.network_channel);
                strength = (ProgressBar)view.findViewById(R.id.network_strength);
            }

            public void hydrate(AccessPoint ap, Long seenTime, AccessPointDataPoint dp) {
                ssid.setText(ap.getSSID());

                // Highlight access points that we didn't see on latest update
                ssid.setTextColor(seenTime < updateTime ? Color.RED : Color.WHITE);

                // Assuming we're talking about a 2.4GHz WiFi source, channels are as follows:
                // Channel 1 - 2412
                // Channel 2 - 2417
                // Channel 3 - 2422
                // ...
                // Channel 13 - 2472
                // and then channel 14 messes everything up at
                // Channel 14 - 2484

                // There's also 5GHz channels, but I'm not worrying about those right now
                int channelNumber = ap.getChannel();
                String channelStr = channelNumber == -1
                                  ? String.format("(%d Hz)", ap.getFrequency())
                                  : Integer.toString(channelNumber);

                channel.setText(channelStr);

                // ScanResult.timestamp is not the last time we saw this AP - rather, it is
                // the synchronized time function of that access point; each network maintains
                // this number such that it is consistent across devices on that network.
                seen.setText("Last seen " +
                    DateUtils.getRelativeDateTimeString(
                        WifiListActivity.this,
                        seenTime,
                        DateUtils.SECOND_IN_MILLIS,
                        DateUtils.DAY_IN_MILLIS,
                        0
                    )
                );

                // The signal strength is measured in milli-watt decibels (relative to 1 milli-watt)
                // Generally, these values are negative and somewhere in the [-100, 0] range, with
                // values on the lower end representing bad signals and values near zero being good
                // signals.
                // Scan.level: [-100, -30]
                //   + 100     [0,     70]
                int maxLevel = 32;
                strength.setMax(maxLevel);
                strength.setProgress(WifiManager.calculateSignalLevel(dp.getLevel(), maxLevel));
            }
        }
    }
}
