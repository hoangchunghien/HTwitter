package me.hoangchunghien.htwitter;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;

/**
 * Created by hienhoang on 2/21/16.
 */
public class LoginFragment extends Fragment {

    static final String LOG_TAG = LoginFragment.class.getSimpleName();

    static interface LoginSuccessCallbacks {
        void onLoginSuccess();
    }

    Twitter mTwitter;
    SharedPreferences mPref;
    LoginSuccessCallbacks mCallbacks;

    Button mLoginButton;
    Dialog mOAuthDialog;

    public LoginFragment() {
    }

    public void setOnLoginSuccessCallbacks(LoginSuccessCallbacks callbacks) {
        mCallbacks = callbacks;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_login, container, false);
        mLoginButton = (Button) root.findViewById(R.id.login_button);

        mPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mTwitter = new TwitterFactory().getInstance();
        mTwitter.setOAuthConsumer(BuildConfig.TWITTER_CONSUMER_KEY, BuildConfig.TWITTER_CONSUMER_SECRET);

        mLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onLoginButtonClick();
            }
        });

        return root;
    }

    private void onLoginButtonClick() {

        fetchTwitterRequestToken()
                .flatMap(new Func1<RequestToken, Observable<AccessToken>>() {
                    @Override
                    public Observable<AccessToken> call(final RequestToken requestToken) {
                        return Observable.create(new Observable.OnSubscribe<String>() {
                            @Override
                            public void call(final Subscriber<? super String> subscriber) {
                                String oauthUrl = requestToken.getAuthorizationURL();
                                if (oauthUrl != null) {
                                    mOAuthDialog = new Dialog(getActivity());
                                    mOAuthDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                                    mOAuthDialog.setContentView(R.layout.dialog_oauth);

                                    WebView webView = (WebView) mOAuthDialog.findViewById(R.id.oauth_webview);
                                    webView.getSettings().setJavaScriptEnabled(true);
                                    webView.loadUrl(oauthUrl);
                                    webView.setWebViewClient(new WebViewClient() {
                                        boolean authCompleted = false;

                                        @Override
                                        public void onPageStarted(WebView view, String url, Bitmap favicon) {
                                            super.onPageStarted(view, url, favicon);
                                        }

                                        @Override
                                        public void onPageFinished(WebView view, String url) {
                                            super.onPageFinished(view, url);
                                            if (url.contains("oauth_verifier") && authCompleted == false) {
                                                authCompleted = true;
                                                Uri uri = Uri.parse(url);
                                                String oauthVerifier = uri.getQueryParameter("oauth_verifier");
                                                subscriber.onNext(oauthVerifier);
                                                mOAuthDialog.dismiss();
                                                subscriber.onCompleted();
                                            } else if (url.contains("denied")) {
                                                mOAuthDialog.dismiss();
                                                subscriber.onError(new Exception("Permission denied"));
                                            }
                                        }
                                    });
                                    mOAuthDialog.show();
                                    mOAuthDialog.setCancelable(true);
                                } else {
                                    subscriber.onError(new Exception("Network error or Invalid Credentials"));
                                }
                            }
                        }).flatMap(new Func1<String, Observable<AccessToken>>() {
                            @Override
                            public Observable<AccessToken> call(final String oauthVerifier) {
                                return Observable.create(new Observable.OnSubscribe<AccessToken>() {
                                    @Override
                                    public void call(Subscriber<? super AccessToken> subscriber) {
                                        try {
                                            AccessToken accessToken = mTwitter.getOAuthAccessToken(requestToken, oauthVerifier);
                                            subscriber.onNext(accessToken);
                                            subscriber.onCompleted();
                                        } catch (Exception e) {
                                            Log.e(LOG_TAG, e.getMessage(), e);
                                            subscriber.onError(e);
                                        }
                                    }
                                }).subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread());
                            }
                        });
                    }
                })
                .subscribe(
                        new Action1<AccessToken>() {
                            @Override
                            public void call(AccessToken accessToken) {
                                Log.d(LOG_TAG, "Access Token: " + accessToken);

                                SharedPreferences.Editor edit = mPref.edit();
                                edit.putString(getString(R.string.pref_access_token), accessToken.getToken());
                                edit.putString(getString(R.string.pref_access_token_secret), accessToken.getTokenSecret());
                                edit.commit();

                                if (mCallbacks != null) {
                                    mCallbacks.onLoginSuccess();
                                }

                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                Toast.makeText(getActivity(), throwable.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                );
    }


    Observable<RequestToken> fetchTwitterRequestToken() {
        Observable<RequestToken> observable = Observable.create(new Observable.OnSubscribe<RequestToken>() {
            @Override
            public void call(Subscriber<? super RequestToken> subscriber) {
                try {

                    RequestToken requestToken = mTwitter.getOAuthRequestToken();
                    Log.d(LOG_TAG, "Request Token: " + requestToken);
                    subscriber.onNext(requestToken);
                    subscriber.onCompleted();

                } catch (Exception e) {
                    Log.e(LOG_TAG, e.getMessage(), e);
                    Log.d(LOG_TAG, "Consumer key: " + BuildConfig.TWITTER_CONSUMER_KEY);
                    Log.d(LOG_TAG, "Consumer secret: " + BuildConfig.TWITTER_CONSUMER_SECRET);
                    subscriber.onError(e);
                }
            }
        });

        return observable
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread());
    }

}
