package ca.klostermann.philip.location_tracker;

import android.util.Log;

import com.firebase.client.AuthData;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;

public class UserLoginTask {
    private final String TAG = "UserLoginTask";
    private final LoginTaskCaller mCaller;
    private final String mFirebaseUrl;
    private final String mEmail;
    private final String mPassword;

    private FirebaseError mError;
    private AuthData mAuthData;

    public UserLoginTask(LoginTaskCaller caller, String firebaseURL, String email, String password) {
        mCaller = caller;
        mFirebaseUrl = firebaseURL;
        mEmail = email;
        mPassword = password;
    }

    public void execute() {
        try {
            Firebase ref = new Firebase(mFirebaseUrl);
            ref.authWithPassword(mEmail, mPassword, new Firebase.AuthResultHandler() {
                @Override
                public void onAuthenticated(AuthData authData) {
                    Log.d(TAG, authData.toString());
                    mAuthData = authData;
                    onComplete(true);
                }

                @Override
                public void onAuthenticationError(FirebaseError firebaseError) {
                    Log.e(TAG, firebaseError.toString());
                    mError = firebaseError;
                    onComplete(false);
                }
            });
        } catch (Exception e) {
            mError = new FirebaseError(
                    FirebaseError.OPERATION_FAILED, e.getMessage());
            Log.e(TAG, e.toString());
            onComplete(false);
        }
    }

    protected void onComplete(final Boolean success) {

        if (success && mAuthData != null) {
            mCaller.onLoginSuccess(mAuthData);
        } else if (!success && mError != null) {
            mCaller.onLoginFailure(mError);
        } else {
            mCaller.onLoginFailure(null);
        }
    }
}