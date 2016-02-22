package me.hoangchunghien.htwitter;

import android.app.SearchManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.SearchView;
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

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import twitter4j.ResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.conf.ConfigurationBuilder;

/**
 * A placeholder fragment containing a simple view.
 */
public class PeopleFragment extends Fragment implements SearchView.OnQueryTextListener {

    static final String LOG_TAG = PeopleFragment.class.getSimpleName();

    Twitter mTwitter;

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private PeopleAdapter mAdapter;
    private TextView mEmptyTextView;

    public PeopleFragment() {
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_people, container, false);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        configurationBuilder.setOAuthConsumerKey(BuildConfig.TWITTER_CONSUMER_KEY);
        configurationBuilder.setOAuthConsumerSecret(BuildConfig.TWITTER_CONSUMER_SECRET);
        String accessTokenStr = sharedPreferences.getString(getString(R.string.pref_access_token), "");
        String accessTokenSecretStr = sharedPreferences.getString(getString(R.string.pref_access_token_secret), "");
        AccessToken accessToken = new AccessToken(accessTokenStr, accessTokenSecretStr);
        mTwitter = new TwitterFactory(configurationBuilder.build()).getInstance(accessToken);

        mSwipeRefreshLayout = (SwipeRefreshLayout) root.findViewById(R.id.swipe_refresh_container);
        mSwipeRefreshLayout.setEnabled(false);
        ListView listView = (ListView) root.findViewById(R.id.people_listview);
        mAdapter = new PeopleAdapter(new ArrayList<User>() {
        });
        listView.setAdapter(mAdapter);

        mEmptyTextView = (TextView) root.findViewById(R.id.people_empty_textview);
        listView.setEmptyView(mEmptyTextView);

        return root;
    }

    private Observable<ResponseList<User>> searchUsers(final String query) {
        Observable<ResponseList<User>> observable = Observable.create(new Observable.OnSubscribe<ResponseList<User>>() {
            @Override
            public void call(Subscriber<? super ResponseList<User>> subscriber) {
                try {
                    ResponseList<User> result = mTwitter.searchUsers(query, 0);
                    subscriber.onNext(result);
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
        inflater.inflate(R.menu.people, menu);

        MenuItem searchItem = menu.findItem(R.id.search);
        SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);

        if (searchItem != null) {
            SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));
            searchView.setOnQueryTextListener(this);
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        mSwipeRefreshLayout.setRefreshing(true);
        searchUsers(query)
                .subscribe(
                        new Action1<ResponseList<User>>() {
                            @Override
                            public void call(ResponseList<User> users) {
                                mAdapter.clear();
                                for (User user : users) {
                                    mAdapter.add(user);
                                }
                                mSwipeRefreshLayout.setRefreshing(false);
                            }
                        },
                        new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                Log.e(LOG_TAG, throwable.getMessage(), throwable);
                                Toast.makeText(getActivity(), throwable.getMessage(), Toast.LENGTH_LONG).show();
                                mSwipeRefreshLayout.setRefreshing(false);
                            }
                        }
                );
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    class PeopleAdapter extends BaseAdapter {

        List<User> mUsers;

        public PeopleAdapter(List<User> users) {
            mUsers = users;
        }

        public void clear() {
            mUsers.clear();
        }

        public void add(User user) {
            mUsers.add(user);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mUsers != null ? mUsers.size() : 0;
        }

        @Override
        public User getItem(int position) {
            return mUsers != null ? mUsers.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            final User user = mUsers.get(position);

            View root;
            ViewHolder holder;
            if (convertView != null) {
                root = convertView;
                holder = (ViewHolder) convertView.getTag();
            } else {
                root = View.inflate(getActivity(), R.layout.listview_item_people, null);
                holder = new ViewHolder();
                holder.textView = (TextView) root.findViewById(R.id.people_item_textview);
                holder.imageView = (ImageView) root.findViewById(R.id.people_item_image);
                holder.button = (Button) root.findViewById(R.id.people_item_follow_button);
                root.setTag(holder);
            }

            Picasso.with(getActivity()).load(user.getOriginalProfileImageURL()).into(holder.imageView);
            holder.textView.setText(user.getName());

            holder.button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    follow(user).subscribe(new Action1<User>() {
                        @Override
                        public void call(User user) {
                            Toast.makeText(getActivity(), "Followed " + user.getName(), Toast.LENGTH_LONG).show();
                        }
                    }, new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            Log.e(LOG_TAG, throwable.getMessage(), throwable);
                            Toast.makeText(getActivity(), throwable.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });


            return root;
        }

        private Observable<User> follow(final User user) {
            return Observable.create(new Observable.OnSubscribe<User>() {
                @Override
                public void call(Subscriber<? super User> subscriber) {
                    try {
                        User result = mTwitter.createFriendship(user.getId());
                        subscriber.onNext(result);
                        subscriber.onCompleted();
                    } catch (Exception e) {
                        subscriber.onError(e);
                    }
                }
            }).subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread());

        }

        class ViewHolder {
            public ImageView imageView;
            public TextView textView;
            public Button button;
        }
    }

}
