package org.itishka.pointim.api.data;

public class Post extends PointResult {
	public long uid;
	public RecommendData rec;
	public PostData post;
	public boolean recommended;
	public boolean editable;
	public boolean subscribed;
	public boolean bookmarked;
    public String comment_id;
}
