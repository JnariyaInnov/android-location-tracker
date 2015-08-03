package ca.klostermann.philip.location_tracker;

import com.firebase.client.FirebaseError;
import java.util.Map;

public interface SignupTaskCaller {
    public abstract void onSignupSuccess(Map<String, Object> result);
    public abstract void onSignupFailure(FirebaseError firebaseError);
}
