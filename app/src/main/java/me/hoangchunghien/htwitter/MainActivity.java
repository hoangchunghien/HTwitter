package me.hoangchunghien.htwitter;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

public class MainActivity extends AppCompatActivity implements
        LoginFragment.LoginSuccessCallbacks, SharedPreferences.OnSharedPreferenceChangeListener {

    SharedPreferences mSharePrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mSharePrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mSharePrefs.registerOnSharedPreferenceChangeListener(this);

        displayContent();
    }

    private void displayContent() {
        if (isUserLoggedIn()) {
            displayHomeFragment();
        }
        else {
            LoginFragment fragment = new LoginFragment();
            fragment.setOnLoginSuccessCallbacks(this);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, fragment)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .commit();
        }
    }

    @Override
    protected void onDestroy() {
        mSharePrefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    private void displayHomeFragment() {
        Fragment homeFragment = new HomeFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, homeFragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit();
    }

    @Override
    public void onLoginSuccess() {
        displayHomeFragment();
    }

    boolean isUserLoggedIn() {
        String accessToken = mSharePrefs.getString(getString(R.string.pref_access_token), "");
        return !accessToken.isEmpty();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_access_token))) {
            displayContent();
        }
    }
}
