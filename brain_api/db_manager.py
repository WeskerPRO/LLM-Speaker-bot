import mysql.connector
from mysql.connector import Error, pooling
import os
import logging
import asyncio # New import for run_in_executor
# Database credentials (store securely, e.g., in environment variables)
# IMPORTANT: Replace "your_mysql_password" with your actual MySQL root password or a dedicated user's password.
DB_HOST = os.getenv("DB_HOST", "localhost")
DB_USER = os.getenv("DB_USER", "root") # Use 'root'
DB_PASSWORD = os.getenv("DB_PASSWORD", "") # Leave empty or None, as there's no password
DB_NAME = os.getenv("DB_NAME", "telegram_bot_db") # Ensure this matches what you created


logger = logging.getLogger(__name__)

def create_db_pool():
    """Establishes a MySQL database connection pool."""
    try:
        pool = mysql.connector.pooling.MySQLConnectionPool(
            pool_name="telegram_bot_pool",
            pool_size=5,  # Adjust as needed
            host=DB_HOST,
            user=DB_USER,
            password=DB_PASSWORD,
            database=DB_NAME,
            connection_timeout=60
        )

        logger.info("Database connection pool established successfully! ✅")
        return pool
    except mysql.connector.Error as err: # Use mysql.connector.Error for specific DB errors
        logger.critical(f"FATAL ERROR: Could not connect to the database or establish pool. Details: {err}", exc_info=True)
        return None
    except Exception as e: # Catch any other unexpected errors
        logger.critical(f"FATAL ERROR: An unexpected error occurred during database pool creation. Details: {e}", exc_info=True)
        return None

async def execute_transaction_query(db_pool: pooling.MySQLConnectionPool, queries: list, params_list: list = None):
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(
        None, # Use the default thread pool executor
        _execute_transaction_query_sync, # The synchronous function to run
        db_pool, queries, params_list
    )


def _execute_transaction_query_sync(db_pool, queries: list, params_list: list = None):
    connection = None
    cursor = None
    try:
        connection = db_pool.get_connection()
        cursor = connection.cursor(dictionary=True)
        
        # Start transaction explicitly via the driver
        connection.start_transaction()
        
        summaries = []
        # Loop through the list of queries
        for i, sql in enumerate(queries):
            params = params_list[i] if params_list else ()
            cursor.execute(sql, params)
            summaries.append({"query_index": i, "affected": cursor.rowcount})
            
        connection.commit()
        return summaries

    except Exception as e:
        if connection:
            connection.rollback()
        print(f"Transaction failed at query {i}: {e}")
        return None
    finally:
        if cursor: cursor.close()
        if connection: connection.close()


async def execute_query(db_pool: pooling.MySQLConnectionPool, query: str, params: tuple = None) -> int | None:
    """
    Executes a single query (INSERT, UPDATE, DELETE) in a separate thread.
    Returns lastrowid for INSERT, or None on failure.
    """
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(
        None, # Use the default thread pool executor
        _execute_query_sync, # The synchronous function to run
        db_pool, query, params
    )

def _execute_query_sync(db_pool: pooling.MySQLConnectionPool, query: str, params: tuple) -> int | None:
    """Synchronous helper for execute_query to be run in a thread pool."""
    connection = None
    cursor = None
    try:
        connection = db_pool.get_connection()
        cursor = connection.cursor()
        cursor.execute(query, params or ())
        connection.commit()
        return cursor.lastrowid # Returns ID for INSERT
    except Error as e:
        logger.error(f"Error executing query: {e}. Query: {query}", exc_info=True)
        if connection:
            connection.rollback() # Rollback on error
        return None
    finally:
        if cursor:
            cursor.close()
        if connection and connection.is_connected():
            connection.close() # Release connection back to the pool

async def fetch_query(db_pool: pooling.MySQLConnectionPool, query: str, params: tuple = None, fetch_one: bool = False):
    """
    Fetches data from a query (SELECT) in a separate thread.
    Args:
        db_pool: The database connection pool.
        query: The SQL query string.
        params: Tuple of parameters for the query.
        fetch_one: If True, fetches a single row; otherwise, fetches all rows.
    Returns:
        A dictionary (if fetch_one) or a list of dictionaries, or None/empty list on error/no results.
    """
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(
        None, # Use the default thread pool executor
        _fetch_query_sync, # The synchronous function to run
        db_pool, query, params, fetch_one
    )

def _fetch_query_sync(db_pool: pooling.MySQLConnectionPool, query: str, params: tuple, fetch_one: bool):
    """Synchronous helper for fetch_query to be run in a thread pool."""
    connection = None
    cursor = None
    try:
        connection = db_pool.get_connection()
        cursor = connection.cursor(dictionary=True) # Returns rows as dictionaries
        cursor.execute(query, params or ())
        if fetch_one:
            result = cursor.fetchone()
        else:
            result = cursor.fetchall()
        return result
    except Error as e:
        logger.error(f"Error fetching data: {e}. Query: {query}", exc_info=True)
        # No rollback needed for SELECTs typically, unless transaction management is complex
        return None if fetch_one else []
    finally:
        if cursor:
            cursor.close()
        if connection and connection.is_connected():
            connection.close() # Release connection back to the pool
