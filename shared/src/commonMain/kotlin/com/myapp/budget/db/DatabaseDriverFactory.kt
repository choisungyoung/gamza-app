package com.myapp.budget.db

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DatabaseDriverFactory): BudgetDatabase {
    val driver = driverFactory.createDriver()
    return BudgetDatabase(driver)
}
