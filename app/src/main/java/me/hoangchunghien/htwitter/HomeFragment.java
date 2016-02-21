package me.hoangchunghien.htwitter;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.conf.ConfigurationBuilder;

/**
 * Created by hienhoang on 2/21/16.
 */
public class HomeFragment extends Fragment {

    static final String LOG_TAG = HomeFragment.class.getSimpleName();

    Twitter mTwitter;

    HomeAdapter mAdapter;


    public HomeFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        ListView listView = (ListView) root.findViewById(R.id.home_listview);
        mAdapter = new HomeAdapter(new ArrayList<Status>());
        listView.setAdapter(mAdapter);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        configurationBuilder.setOAuthConsumerKey(BuildConfig.TWITTER_CONSUMER_KEY);
        configurationBuilder.setOAuthConsumerSecret(BuildConfig.TWITTER_CONSUMER_SECRET);
        String accessTokenStr = sharedPreferences.getString(getString(R.string.pref_access_token), "");
        String accessTokenSecretStr = sharedPreferences.getString(getString(R.string.pref_access_token_secret), "");
        AccessToken accessToken = new AccessToken(accessTokenStr, accessTokenSecretStr);
        mTwitter = new TwitterFactory(configurationBuilder.build()).getInstance(accessToken);

        Log.d(LOG_TAG, "Access token: " + accessTokenStr);
        Log.d(LOG_TAG, "Access token secret: " + accessTokenSecretStr);
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(LOG_TAG, "onStart");
        fetchHomeTimeline();
    }

    private void fetchHomeTimeline() {
        Observable<List<Status>> observable = Observable.create(new Observable.OnSubscribe<List<Status>>() {
            @Override
            public void call(Subscriber<? super List<Status>> subscriber) {
                try {
                    List<Status> statuses = mTwitter.getHomeTimeline();
                    subscriber.onNext(statuses);
                    subscriber.onCompleted();
                } catch (Exception e) {
                    subscriber.onError(e);
                }
            }
        });

        observable.subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<List<Status>>() {
                    @Override
                    public void call(List<Status> statuses) {
                        mAdapter.clear();
                        for (Status item : statuses) {
                            mAdapter.add(item);
                        }
                    }
                });
    }

    class HomeAdapter extends BaseAdapter {

        List<Status> statusList;

        public HomeAdapter(List<Status> statuses) {
            statusList = statuses;
        }

        public void clear() {
            statusList.clear();
        }

        public void add(Status status) {
            statusList.add(status);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return statusList != null ? statusList.size() : 0;
        }

        @Override
        public Status getItem(int position) {
            return statusList != null ? statusList.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            Status status = statusList.get(position);

            View root;
            ViewHolder holder;
            if (convertView != null) {
                root = convertView;
                holder = (ViewHolder) convertView.getTag();
            }
            else {
                root = View.inflate(getActivity(), R.layout.listview_item_home, null);
                holder = new ViewHolder();
                holder.textView = (TextView) root.findViewById(R.id.item_textview);
                root.setTag(holder);
            }

            holder.textView.setText(status.getText());
            return root;
        }

        class ViewHolder {
            public TextView textView;
        }
    }

}

