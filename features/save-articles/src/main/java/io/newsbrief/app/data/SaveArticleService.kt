package io.newsbrief.app.data

object SaveArticleService {
    suspend fun toggle(db: NewsStore, article: ArticleEntity): Boolean = db.transaction {
        if (db.savedArticleDao().contains(article.link)) {
            db.savedArticleDao().remove(article.link); false
        } else {
            db.articleDao().insertAll(listOf(article))
            db.savedArticleDao().insert(SavedArticleEntity(article.link)); true
        }
    }
}
