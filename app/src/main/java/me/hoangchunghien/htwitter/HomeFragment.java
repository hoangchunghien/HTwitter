package me.hoangchunghien.htwitter;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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
public class HomeFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

    static final String LOG_TAG = HomeFragment.class.getSimpleName();

    Timer mTimer;

    Twitter mTwitter;
    SharedPreferences mSharedPrefs;

    HomeAdapter mAdapter;
    SwipeRefreshLayout mSwipeRefreshLayout;

    View mEmptyViewContainer;
    TextView mEmptyTextView;
    Button mFindPeopleButton;

    public HomeFragment() {
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        mSwipeRefreshLayout = (SwipeRefreshLayout) root.findViewById(R.id.swipe_refresh_container);
        mSwipeRefreshLayout.setProgressViewOffset(false, 0, 300);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        mEmptyViewContainer = root.findViewById(R.id.home_empty_container);
        mEmptyTextView = (TextView) root.findViewById(R.id.home_empty_textview);
        mFindPeopleButton = (Button) root.findViewById(R.id.home_find_people_button);
        mFindPeopleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), PeopleActivity.class));
            }
        });
        mEmptyViewContainer.setVisibility(View.INVISIBLE);

        ListView listView = (ListView) root.findViewById(R.id.home_listview);
        listView.setEmptyView(mEmptyViewContainer);
        mAdapter = new HomeAdapter(new ArrayList<Status>());
        listView.setAdapter(mAdapter);

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        configurationBuilder.setOAuthConsumerKey(BuildConfig.TWITTER_CONSUMER_KEY);
        configurationBuilder.setOAuthConsumerSecret(BuildConfig.TWITTER_CONSUMER_SECRET);
        String accessTokenStr = mSharedPrefs.getString(getString(R.string.pref_access_token), "");
        String accessTokenSecretStr = mSharedPrefs.getString(getString(R.string.pref_access_token_secret), "");
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

        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                fetchHomeTimelineObservable()
                        .subscribe(new Action1<List<Status>>() {
                            @Override
                            public void call(List<Status> statuses) {
                                Log.d(LOG_TAG, "Updating...");
                                List<Status> diffStatus = mAdapter.findDifferent(statuses);
                                if (diffStatus.size() > 0) {
                                    for (Status status : diffStatus) {
                                        mAdapter.push(status);
                                    }
                                    Toast.makeText(getActivity(), diffStatus.size() + " new tweets found", Toast.LENGTH_LONG).show();
                                }
                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                Toast.makeText(getActivity(), throwable.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
            }
        }, 0, 20000); // Refresh every 20 seconds
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mTimer != null) {
            mTimer.cancel();
        }
    }

    private void fetchHomeTimeline() {
        mSwipeRefreshLayout.setRefreshing(true);
        fetchHomeTimelineObservable().subscribe(
                new Action1<List<Status>>() {
                    @Override
                    public void call(List<Status> statuses) {
                        if (statuses.size() > 0) {
                            mAdapter.clear();
                            for (Status item : statuses) {
                                mAdapter.add(item);
                            }
                        } else {
                            mEmptyTextView.setText("Your timeline is empty, please follow at least 5 users to gain the best of the app");
                            mEmptyViewContainer.setVisibility(View.VISIBLE);
                            mSwipeRefreshLayout.setEnabled(false);
                        }
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Log.e(LOG_TAG, throwable.getMessage(), throwable);
                        Toast.makeText(getActivity(), throwable.getMessage(), Toast.LENGTH_LONG).show();
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                });
    }

    Observable<List<Status>> fetchHomeTimelineObservable() {
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

        return observable
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.home, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_logout) {
            SharedPreferences.Editor editor = mSharedPrefs.edit();
            editor.putString(getString(R.string.pref_access_token), "");
            editor.putString(getString(R.string.pref_access_token_secret), "");
            editor.commit();
            return true;
        }
        else if (id == R.id.action_find_people) {
            startActivity(new Intent(getActivity(), PeopleActivity.class));
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRefresh() {
        fetchHomeTimeline();
    }

    class HomeAdapter extends BaseAdapter {

        List<Status> statusList;

        public HomeAdapter(List<Status> statuses) {
            statusList = statuses;
        }

        public void clear() {
            statusList.clear();
        }

        public void push(Status status) {
            statusList.add(0, status);
            notifyDataSetChanged();
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
            } else {
                root = View.inflate(getActivity(), R.layout.listview_item_home, null);
                holder = new ViewHolder();
                holder.textView = (TextView) root.findViewById(R.id.item_textview);
                holder.imageView = (ImageView) root.findViewById(R.id.home_item_image);
                root.setTag(holder);
            }

            holder.textView.setText(status.getText());
            Picasso.with(getActivity()).load(status.getUser().getOriginalProfileImageURL()).into(holder.imageView);
            return root;
        }

        public List<Status> findDifferent(List<Status> statuses) {
            List<Status> diffStatus = new ArrayList<>();
            for (Status status: statuses) {
                boolean found = false;
                for (Status item : statusList) {
                    if (item.getId() == status.getId()) {
                        found = true;
                        break;
                    }
                }
                if (!found)
                    diffStatus.add(status);
            }
            return diffStatus;
        }

        class ViewHolder {
            public TextView textView;
            public ImageView imageView;
        }
    }

}

