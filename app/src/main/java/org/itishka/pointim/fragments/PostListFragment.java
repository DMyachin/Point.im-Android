package org.itishka.pointim.fragments;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.octo.android.robospice.persistence.DurationInMillis;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;

import org.itishka.pointim.R;
import org.itishka.pointim.activities.SinglePostActivity;
import org.itishka.pointim.activities.TagViewActivity;
import org.itishka.pointim.adapters.PostListAdapter;
import org.itishka.pointim.api.ConnectionManager;
import org.itishka.pointim.model.Post;
import org.itishka.pointim.model.PostList;
import org.itishka.pointim.network.requests.PostListRequest;

import java.util.List;


/**
 * A simple {@link Fragment} subclass.
 */
public abstract class PostListFragment extends SpicedFragment {

    private PostListAdapter.OnPostClickListener mOnPostClickListener = new PostListAdapter.OnPostClickListener() {
        @Override
        public void onPostClicked(View view, String post) {
            Intent intent = new Intent(getActivity(), SinglePostActivity.class);
            intent.putExtra("post", post);
            ActivityCompat.startActivity(getActivity(), intent, null);
        }

        @Override
        public void onTagClicked(View view, String tag) {
            Intent intent = new Intent(getActivity(), TagViewActivity.class);
            intent.putExtra("tag", tag);
            ActivityCompat.startActivity(getActivity(), intent, null);
        }
    };

    private RecyclerView mRecyclerView;
    private StaggeredGridLayoutManager mLayoutManager;
    private PostListAdapter mAdapter;
    private SwipeRefreshLayout mSwipeRefresh;

    private RequestListener<PostList> mUpdateRequestListener = new RequestListener<PostList>() {
        @Override
        public void onRequestFailure(SpiceException spiceException) {
            mSwipeRefresh.setRefreshing(false);
            if (!isDetached())
                Toast.makeText(getActivity(), spiceException.toString(), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onRequestSuccess(PostList postList) {
            mSwipeRefresh.setRefreshing(false);
            if (postList != null && postList.isSuccess()) {
                mAdapter.setData(postList);
                mRecyclerView.scrollToPosition(0);
            } else {
                if (!isDetached())
                    Toast.makeText(getActivity(), (postList == null) ? "null" : postList.error, Toast.LENGTH_SHORT).show();
            }
        }
    };
    private RequestListener<PostList> mCacheRequestListener = new RequestListener<PostList>() {
        @Override
        public void onRequestFailure(SpiceException spiceException) {
        }

        @Override
        public void onRequestSuccess(PostList postList) {
            if (postList != null && postList.isSuccess()) {
                mAdapter.setData(postList);
            }
        }
    };
    private boolean mIsLoadingMore = false;
    private RequestListener<PostList> mLoadMoreRequestListener = new RequestListener<PostList>() {

        @Override
        public void onRequestFailure(SpiceException spiceException) {
            if (!isDetached())
                Toast.makeText(getActivity(), spiceException.toString(), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onRequestSuccess(PostList postList) {
            if (postList != null && postList.isSuccess()) {
                mAdapter.appendData(postList);
            } else {
                if (!isDetached())
                    Toast.makeText(getActivity(), (postList == null) ? "null" : postList.error, Toast.LENGTH_SHORT).show();
            }
        }
    };

    public PostListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_posts_fragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            mSwipeRefresh.setRefreshing(true);
            update();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mLayoutManager.setSpanCount(getSpanCount(newConfig));
    }

    private int getSpanCount(Configuration config) {
        SharedPreferences prefs = getActivity().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("multiColumns", true))
            return 1;

        if ((config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_LARGE) {
            return config.orientation == Configuration.ORIENTATION_PORTRAIT ? 2 : 3;
        } else if ((config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_XLARGE) {
            return config.orientation == Configuration.ORIENTATION_PORTRAIT ? 2 : 3;
        } else if ((config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_NORMAL) {
            return config.orientation == Configuration.ORIENTATION_PORTRAIT ? 1 : 2;
        }
        return 1;
    }

    public PostListAdapter getAdapter() {
        return mAdapter;
    }

    protected PostListAdapter createAdapter() {
        return new PostListAdapter(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.posts);
        mSwipeRefresh = (SwipeRefreshLayout) rootView.findViewById(R.id.swiperefresh);
        mSwipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                ConnectionManager manager = ConnectionManager.getInstance();
                if (manager.isAuthorized()) {
                    update();
                }
            }
        });
        mRecyclerView.setHasFixedSize(true);
        mLayoutManager = new StaggeredGridLayoutManager(
                getSpanCount(getActivity().getResources().getConfiguration()),
                StaggeredGridLayoutManager.VERTICAL
        );
        mRecyclerView.setLayoutManager(mLayoutManager);
        mAdapter = createAdapter();
        mAdapter.setOnPostClickListener(mOnPostClickListener);
        mAdapter.setOnLoadMoreRequestListener(new PostListAdapter.OnLoadMoreRequestListener() {
            @Override
            public boolean onLoadMoreRequested() {
                if (mIsLoadingMore) {
                    //do nothing
                } else {
                    List<Post> posts = mAdapter.getPostList().posts;
                    if (posts.size() < 1) {
                        mAdapter.getPostList().has_next = false;
                        return false;
                    } else {
                        loadMore(posts.get(posts.size() - 1).uid);
                    }
                }
                return true;
            }
        });
        mRecyclerView.setAdapter(mAdapter);
        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ConnectionManager manager = ConnectionManager.getInstance();
        if (manager.isAuthorized()) {
            PostListRequest request = createRequest();
            getSpiceManager().getFromCache(PostList.class, request.getCacheName(), DurationInMillis.ALWAYS_RETURNED, mCacheRequestListener);
            mSwipeRefresh.post(new Runnable() {
                @Override
                public void run() {
                    mSwipeRefresh.setRefreshing(true);
                    update();
                }
            });
        }
    }

    protected void update() {
        PostListRequest request = createRequest();
        getSpiceManager().execute(request, request.getCacheName(), DurationInMillis.ALWAYS_EXPIRED, mUpdateRequestListener);
    }

    protected void loadMore(long before) {
        PostListRequest request = createRequest(before);
        getSpiceManager().execute(request, request.getCacheName(), DurationInMillis.ALWAYS_EXPIRED, mLoadMoreRequestListener);
    }

    protected abstract PostListRequest createRequest();

    protected abstract PostListRequest createRequest(long before);
}
