package io.github.xccxcxxc.brook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query("SELECT * FROM folders ORDER BY sortIndex, title COLLATE NOCASE")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM feeds ORDER BY sortIndex, title COLLATE NOCASE")
    fun observeFeeds(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds")
    suspend fun allFeeds(): List<FeedEntity>

    @Upsert
    suspend fun upsertFolders(folders: List<FolderEntity>)

    @Upsert
    suspend fun upsertFeeds(feeds: List<FeedEntity>)

    @Query("DELETE FROM folders WHERE id NOT IN (:keep)")
    suspend fun deleteFoldersExcept(keep: List<String>)

    @Query("DELETE FROM feeds WHERE id NOT IN (:keep)")
    suspend fun deleteFeedsExcept(keep: List<String>)

    @Query("UPDATE feeds SET localIcon = :icon WHERE id = :id")
    suspend fun setLocalIcon(id: String, icon: String)

    @Query("DELETE FROM folders")
    suspend fun clearFolders()

    @Query("DELETE FROM feeds")
    suspend fun clearFeeds()
}

@Dao
interface ArticleDao {
    @RawQuery
    suspend fun listItems(query: SupportSQLiteQuery): List<ArticleListItem>

    @RawQuery
    suspend fun ids(query: SupportSQLiteQuery): List<Long>

    @Query("SELECT feedId, COUNT(*) AS cnt FROM articles WHERE isRead = 0 GROUP BY feedId")
    fun observeUnreadCounts(): Flow<List<FeedCount>>

    @Query("SELECT feedId, COUNT(*) AS cnt FROM articles WHERE isStarred = 1 GROUP BY feedId")
    fun observeStarredCounts(): Flow<List<FeedCount>>

    @Query("SELECT feedId, COUNT(*) AS cnt FROM articles GROUP BY feedId")
    fun observeAllCounts(): Flow<List<FeedCount>>

    @Query(
        """
        SELECT a.id, a.feedId, COALESCE(f.title, '') AS feedTitle, a.title, a.author, a.url,
               a.publishedAt, a.readingMinutes, a.isRead, a.isStarred, c.html, c.fullHtml
        FROM articles a
        LEFT JOIN feeds f ON f.id = a.feedId
        LEFT JOIN article_content c ON c.articleId = a.id
        WHERE a.id = :id
        """,
    )
    suspend fun readerArticle(id: Long): ReaderArticle?

    /** 离线图片要预下载的文章：这些订阅源里未读或加了星标的，新的在前。 */
    @Query("SELECT id FROM articles WHERE feedId IN (:feedIds) AND (isRead = 0 OR isStarred = 1) ORDER BY publishedAt DESC")
    suspend fun offlineCandidateIds(feedIds: List<String>): List<Long>

    @Query("SELECT id, isRead, isStarred FROM articles WHERE id = :id")
    fun observeFlags(id: Long): Flow<ArticleFlags?>

    @Query("SELECT isRead FROM articles WHERE id = :id")
    suspend fun isRead(id: Long): Boolean?

    @Query("SELECT id, isRead, isStarred FROM articles")
    suspend fun allFlags(): List<ArticleFlags>

    @Query("SELECT id FROM articles")
    suspend fun allIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<ArticleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContents(contents: List<ArticleContentEntity>)

    @Query("UPDATE article_content SET fullHtml = :html WHERE articleId = :id")
    suspend fun setFullHtml(id: Long, html: String)

    @Query("UPDATE articles SET isRead = :read WHERE id IN (:ids)")
    suspend fun setRead(ids: List<Long>, read: Boolean)

    @Query("UPDATE articles SET isStarred = :starred WHERE id IN (:ids)")
    suspend fun setStarred(ids: List<Long>, starred: Boolean)

    @Query("SELECT MAX(fetchedAt) FROM articles")
    suspend fun lastFetchedAt(): Long?

    /** 清理：已读、未加星且早于 [before] 的文章。 */
    @Query("DELETE FROM articles WHERE isRead = 1 AND isStarred = 0 AND publishedAt < :before")
    suspend fun deleteOldRead(before: Long): Int

    @Query("DELETE FROM articles WHERE feedId NOT IN (SELECT id FROM feeds)")
    suspend fun deleteOrphans(): Int

    @Query("DELETE FROM article_content WHERE articleId NOT IN (SELECT id FROM articles)")
    suspend fun deleteOrphanContent(): Int

    @Query("DELETE FROM articles")
    suspend fun clearArticles()

    @Query("DELETE FROM article_content")
    suspend fun clearContent()
}

@Dao
interface PendingDao {
    @Insert
    suspend fun insert(actions: List<PendingActionEntity>)

    @Query("SELECT * FROM pending_actions ORDER BY id")
    suspend fun all(): List<PendingActionEntity>

    @Query("SELECT DISTINCT articleId FROM pending_actions")
    suspend fun pendingArticleIds(): List<Long>

    @Query("DELETE FROM pending_actions WHERE id <= :maxId")
    suspend fun deleteUpTo(maxId: Long)

    @Query("DELETE FROM pending_actions")
    suspend fun clear()
}
