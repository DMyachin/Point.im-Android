package org.itishka.pointim.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.ShareActionProvider;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.itishka.pointim.R;
import org.itishka.pointim.adapters.SinglePostAdapter;
import org.itishka.pointim.listeners.OnCommentChangedListener;
import org.itishka.pointim.listeners.OnPostChangedListener;
import org.itishka.pointim.listeners.SimpleCommentActionsListener;
import org.itishka.pointim.listeners.SimplePointClickListener;
import org.itishka.pointim.listeners.SimplePostActionsListener;
import org.itishka.pointim.model.point.Comment;
import org.itishka.pointim.model.point.Post;
import org.itishka.pointim.network.PointConnectionManager;
import org.itishka.pointim.widgets.ScrollButton;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class SinglePostFragment extends SpicedFragment {
    private static final String ARG_POST = "post";

    private String mPost;
    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mSwipeRefresh;
    private LinearLayoutManager mLayoutManager;
    private SinglePostAdapter mAdapter;
    private Post mPointPost;
    private ShareActionProvider mShareActionProvider;
    private ScrollButton mUpButton;
    private ScrollButton mDownButton;
    private ReplyFragment mReplyFragment;

    private SimplePointClickListener mOnPointClickListener = new SimplePointClickListener(this);
    private SimplePostActionsListener mOnPostActionsListener = new SimplePostActionsListener(this);
    private SimpleCommentActionsListener mOnCommentActionsListener = new SimpleCommentActionsListener(this);
    private OnPostChangedListener mOnPostChangedListener = new OnPostChangedListener() {
        @Override
        public void onChanged(Post post) {
            mAdapter.notifyItemChanged(0);
        }

        @Override
        public void onDeleted(Post post) {
            if (!isDetached())
                getActivity().finish();
        }
    };
    private OnCommentChangedListener mOnCommentChangedListener = new OnCommentChangedListener() {
        @Override
        public void onCommentChanged(Post post, Comment comment) {
            mAdapter.notifyCommentChanged(comment);
//            SinglePostRequest request = createRequest();
//            getSpiceManager().putInCache(request.getCacheName(), mPointPost);
        }

        @Override
        public void onCommentDeleted(Post post, Comment comment) {
            mAdapter.removeComment(comment);
//            SinglePostRequest request = createRequest();
//            getSpiceManager().putInCache(request.getCacheName(), mPointPost);
        }
    };
    private Subscription mCacheSubscription;

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSubscription.unsubscribe();
        mCacheSubscription.unsubscribe();
    }

    private Subscription mSubscription;

    public SinglePostFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param post Post ID.
     * @return A new instance of fragment SinglePostFragment.
     */
    public static SinglePostFragment newInstance(String post) {
        SinglePostFragment fragment = new SinglePostFragment();
        Bundle args = new Bundle();
        args.putString(ARG_POST, post);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mPost = getArguments().getString(ARG_POST);
        }
        setHasOptionsMenu(true);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mRecyclerView = (RecyclerView) view.findViewById(R.id.post);
        mSwipeRefresh = (SwipeRefreshLayout) view.findViewById(R.id.swiperefresh);
        mSwipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                PointConnectionManager manager = PointConnectionManager.getInstance();
                if (manager.isAuthorized()) {
                    update();
                }
            }
        });
        mRecyclerView.setHasFixedSize(true);
        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);
        mAdapter = new SinglePostAdapter(getActivity());
        mRecyclerView.setAdapter(mAdapter);
        mUpButton = (ScrollButton) view.findViewById(R.id.scroll_up);
        mUpButton.setRecyclerView(mRecyclerView);
        mDownButton = (ScrollButton) view.findViewById(R.id.scroll_down);
        mDownButton.setRecyclerView(mRecyclerView);

        mReplyFragment = (ReplyFragment) getChildFragmentManager().findFragmentById(R.id.bottom_bar);

        mOnPostActionsListener.setOnPostChangedListener(mOnPostChangedListener);
        mAdapter.setOnPostActionsListener(mOnPostActionsListener);
        mOnCommentActionsListener.setOnCommentChangedListener(mOnCommentChangedListener);
        mAdapter.setOnCommentActionsListener(mOnCommentActionsListener);
        mAdapter.setOnPointClickListener(mOnPointClickListener);
        mAdapter.setOnCommentClickListener(new SinglePostAdapter.OnItemClickListener() {
            @Override
            public void onCommentClicked(View view, String commentId) {
                mReplyFragment.setCommentId(commentId);
            }

            @Override
            public void onPostClicked(View view) {
                mReplyFragment.setCommentId(null);
            }
        });

        PointConnectionManager manager = PointConnectionManager.getInstance();
        if (manager.isAuthorized()) {
            Observable<Post> observable = getCache().get("SinglePostFragment" + mPost, Post.class);
            mCacheSubscription = observable
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe(post -> {
                        mSwipeRefresh.setRefreshing(false);
                        if (post != null && post.isSuccess()) {
                            mAdapter.setData(post);
                            mPointPost = post;
                            mReplyFragment.addAuthorsToCompletion(mPointPost);
                            mDownButton.updateVisibility();
                            if (!isDetached())
                                getActivity().supportInvalidateOptionsMenu();
                        }
                        update();
                    });
        }
        mReplyFragment = ReplyFragment.newInstance(mPost);
        getChildFragmentManager().beginTransaction().replace(R.id.fragment_reply, mReplyFragment).commit();
        mReplyFragment.setOnReplyListener(() -> update());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_single_post, container, false);
    }

    protected Observable<Post> createRequest() {
        return PointConnectionManager.getInstance().pointIm.getPost(mPost);
    }

    protected void update() {
        Observable<Post> request = createRequest();
        mSwipeRefresh.setRefreshing(true);
        mSubscription = request
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(post -> {
                    mSwipeRefresh.setRefreshing(false);
                    if (post != null && post.isSuccess()) {
                        getCache().put("SinglePostFragment" + mPost, post);
                        mAdapter.setData(post);
                        mPointPost = post;
                        mReplyFragment.addAuthorsToCompletion(mPointPost);
                        mDownButton.updateVisibility();
                        if (!isDetached())
                            getActivity().supportInvalidateOptionsMenu();
                    } else {
                        if (!isDetached())
                            Toast.makeText(getActivity(), (post == null) ? "null" : post.error, Toast.LENGTH_SHORT).show();
                    }
                }, error -> {
                    mSwipeRefresh.setRefreshing(false);
                    if (!isDetached())
                        Toast.makeText(getActivity(), error.toString(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (mPointPost != null)
            mOnPostActionsListener.updateMenu(menu, mShareActionProvider, mPointPost);

        menu.setGroupVisible(R.id.group_loaded, mPointPost != null);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_single_post, menu);

        MenuItem item = menu.findItem(R.id.menu_item_share);
        mShareActionProvider = new ShareActionProvider(getActivity());
        MenuItemCompat.setActionProvider(item, mShareActionProvider);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            mSwipeRefresh.setRefreshing(true);
            update();
            return true;
        } else {
            mOnPostActionsListener.onMenuClicked(mPointPost, null, item);
        }
        return super.onOptionsItemSelected(item);
    }


}
