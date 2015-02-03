package org.itishka.pointim.fragments;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import org.itishka.pointim.R;
import org.itishka.pointim.adapters.PostListAdapter;
import org.itishka.pointim.adapters.UserInfoPostListAdapter;
import org.itishka.pointim.api.ConnectionManager;
import org.itishka.pointim.model.PointResult;
import org.itishka.pointim.model.PostList;
import org.itishka.pointim.model.User;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * A placeholder fragment containing a simple view.
 */
public class UserViewFragment extends PostListFragment {

    private String mUser;
    private Callback<User> mUserInfoCallback =new Callback<User>() {
        @Override
        public void success(User user, Response response) {
            if (user.isSuccess()) {
                ((UserInfoPostListAdapter)getAdapter()).setUserInfo(user);
            } else if (!isDetached()) {
                Toast.makeText(getActivity(), "Error: "+user.error, Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            if (!isDetached()) {
                Toast.makeText(getActivity(), retrofitError.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    };

    public static UserViewFragment newInstance(String tag) {
        UserViewFragment fragment = new UserViewFragment();
        Bundle args = new Bundle();
        args.putString("user", tag);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected PostListAdapter createAdapter() {
        return new UserInfoPostListAdapter(getActivity());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUser = getArguments().getString("user");
    }

    @Override
    protected void update(Callback<PostList> callback) {
        ConnectionManager.getInstance().pointIm.getBlog(mUser, callback);
        ConnectionManager.getInstance().pointIm.getUserInfo(mUser, mUserInfoCallback);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_subscribe) {
            ConnectionManager.getInstance().pointIm.subscribeUser(mUser, new Callback<PointResult>() {
                @Override
                public void success(PointResult postList, Response response) {
                    if (postList.isSuccess()) {
                        Toast.makeText(getActivity(), "Subscribed!", Toast.LENGTH_SHORT).show();
                    } else {
                        if (!isDetached())
                            Toast.makeText(getActivity(), postList.error, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void failure(RetrofitError error) {
                    if (!isDetached())
                        Toast.makeText(getActivity(), error.toString(), Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        } else if (id == R.id.action_unsubscribe) {
            ConnectionManager.getInstance().pointIm.unsubscribeUser(mUser, new Callback<PointResult>() {
                @Override
                public void success(PointResult postList, Response response) {
                    if (postList.isSuccess()) {
                        Toast.makeText(getActivity(), "Unsubscribed!", Toast.LENGTH_SHORT).show();
                    } else {
                        if (!isDetached())
                            Toast.makeText(getActivity(), postList.error, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void failure(RetrofitError error) {
                    if (!isDetached())
                        Toast.makeText(getActivity(), error.toString(), Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_user, menu);

    }

    @Override
    protected void loadMore(long before, Callback<PostList> callback) {
        ConnectionManager.getInstance().pointIm.getBlog(before, mUser, callback);
    }
}
