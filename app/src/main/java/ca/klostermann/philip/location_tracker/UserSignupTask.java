package ca.klostermann.philip.location_tracker;

import android.util.Log;

import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;

import java.util.Map;

public class UserSignupTask {
    private final String TAG = "UserSignupTask";

    private final SignupTaskCaller mCaller;
    private final String mFirebaseUrl;
    private final String mEmail;
    private final String mPassword;

    private FirebaseError mError;
    private Map<String, Object> mResult;

    UserSignupTask(SignupTaskCaller caller, String firebaseURL, String email, String password) {
        mCaller = caller;
        mFirebaseUrl = firebaseURL;
        mEmail = email;
        mPassword = password;
    }

    public void execute() {
        try {
            Firebase ref = new Firebase(mFirebaseUrl);
            ref.createUser(mEmail, mPassword, new Firebase.ValueResultHandler<Map<String, Object>>() {
                @Override
                public void onSuccess(Map<String, Object> result) {
                    mResult = result;
                    onComplete(true);
                }

                @Override
                public void onError(FirebaseError firebaseError) {
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
        if (success && mResult != null) {
            mCaller.onSignupSuccess(mResult);
        } else if (!success && mError != null) {
            mCaller.onSignupFailure(mError);
        } else {
            mCaller.onSignupFailure(null);
        }
    }
}