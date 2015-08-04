package ca.klostermann.philip.location_tracker;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.firebase.client.AuthData;
import com.firebase.client.FirebaseError;

import java.util.Map;

public class LoginActivity extends Activity implements
        LoginTaskCaller, SignupTaskCaller {

    private static final String TAG = "LocationTracker/Login";

    private AutoCompleteTextView mEmailView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setupActionBar();

        // Set up the login form.
        mEmailView = (AutoCompleteTextView) findViewById(R.id.email);

        mPasswordView = (EditText) findViewById(R.id.password);

        Button mEmailSignInButton = (Button) findViewById(R.id.email_sign_in_button);
        mEmailSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mPasswordView.getWindowToken(), 0);
                imm.hideSoftInputFromWindow(mEmailView.getWindowToken(), 0);
                attemptLogin();
            }
        });

        Button mEmailSignUpButton = (Button) findViewById(R.id.email_sign_up_button);
        mEmailSignUpButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mPasswordView.getWindowToken(), 0);
                imm.hideSoftInputFromWindow(mEmailView.getWindowToken(), 0);
                attemptSignup();
            }
        });

        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);

        //Load credentials from Prefs (if any)
        String storedEmail = Prefs.getUserEmail(this);
        if(storedEmail != null) {
            mEmailView.setText(storedEmail);
        }
        String storedPassword = Prefs.getUserPassword(this);
        if(storedEmail != null) {
            mPasswordView.setText(storedPassword);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setupActionBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            // Show the Up button in the action bar.
            if(getActionBar() != null) {
                getActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }
    }


    public void attemptLogin() {
        if(!validateForm()) {
            return;
        }

        // Store values at the time of the login attempt.
        String email = mEmailView.getText().toString();
        String password = mPasswordView.getText().toString();

        // Show a progress spinner, and kick off a background task to
        // perform the user login attempt.
        showProgress(true);
        UserLoginTask loginTask = new UserLoginTask(
                this, Prefs.getEndpoint(this), email, password);
        loginTask.execute();
    }

    @Override
    public void onLoginSuccess(AuthData authData) {
        showProgress(false);
        Intent returnIntent = new Intent();
        returnIntent.putExtra("uid", authData.getUid());
        returnIntent.putExtra("email", mEmailView.getText().toString());
        returnIntent.putExtra("password", mPasswordView.getText().toString());
        setResult(RESULT_OK, returnIntent);
        finish();
    }

    @Override
    public void onLoginFailure(FirebaseError firebaseError) {
        if(firebaseError == null) {
            firebaseError = new FirebaseError(
                    FirebaseError.OPERATION_FAILED, "Firebase Auth completely failed.");
        }
        Log.d(TAG, "authentication failed: " + firebaseError.getMessage());
        showProgress(false);
        handleFirebaseError(firebaseError);
    }


    public void attemptSignup() {
        if(!validateForm()) {
            return;
        }

        // Store values at the time of the signup attempt.
        String email = mEmailView.getText().toString();
        String password = mPasswordView.getText().toString();

        showProgress(true);
        UserSignupTask signupTask = new UserSignupTask(
                this, Prefs.getEndpoint(this), email, password);
        signupTask.execute();
    }

    @Override
    public void onSignupSuccess(Map<String, Object> result) {
        Log.d(TAG, "Successfully created user account with uid: " + result.get("uid"));
        Toast.makeText(LoginActivity.this, "New Account created.", Toast.LENGTH_LONG).show();
        attemptLogin();
    }

    @Override
    public void onSignupFailure(FirebaseError firebaseError) {
        if(firebaseError == null) {
            firebaseError = new FirebaseError(
                    FirebaseError.OPERATION_FAILED, "Firebase Signup completely failed.");
        }
        Log.d(TAG, "Creating new Account failed: " + firebaseError.toString());
        showProgress(false);
        handleFirebaseError(firebaseError);
    }


    public boolean validateForm() {
        // Reset errors.
        mEmailView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String email = mEmailView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean valid = true;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            valid = false;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            mEmailView.setError(getString(R.string.error_field_required));
            focusView = mEmailView;
            valid = false;
        } else if (!isEmailValid(email)) {
            mEmailView.setError(getString(R.string.error_invalid_email));
            focusView = mEmailView;
            valid = false;
        }
        if (!valid) {
            focusView.requestFocus();
        }

        return valid;
    }

    private boolean isEmailValid(String email) {
        return email.contains("@");
    }

    private boolean isPasswordValid(String password) {
        return password.length() > 4;
    }

    private void handleFirebaseError(FirebaseError error) {
        View focusView = null;

        switch (error.getCode()) {
            case FirebaseError.EMAIL_TAKEN:
                mEmailView.setError(getString(R.string.error_email_taken));
                focusView = mEmailView;
                break;
            case FirebaseError.INVALID_EMAIL:
            case FirebaseError.USER_DOES_NOT_EXIST:
                mEmailView.setError(getString(R.string.error_invalid_email));
                focusView = mEmailView;
                break;
            case FirebaseError.INVALID_PASSWORD:
                mPasswordView.setError(getString(R.string.error_incorrect_password));
                focusView = mPasswordView;
                break;
            default:
                AlertDialog alertDialog = new AlertDialog.Builder(this).create();
                alertDialog.setTitle("Authentication Error");
                alertDialog.setMessage(error.getMessage());
                alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                alertDialog.show();
        }

        if (focusView != null) {
            focusView.requestFocus();
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }
}

