package com.aikansh.ca3

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.*
import com.aikansh.ca3.ui.theme.CA3Theme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phoneNumber: String = ""
)

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY id ASC")
    fun getContacts(): Flow<List<Contact>>

    @Insert
    suspend fun insert(contact: Contact)

    @Query("DELETE FROM contacts WHERE id = (SELECT MAX(id) FROM contacts)")
    suspend fun deleteLastContact()
}

@Database(entities = [Contact::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "contact_db"
            ).build().also { instance = it }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val contactDao = AppDatabase.getDatabase(applicationContext).contactDao()
        setContent {
            CA3Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    ContactScreen(contactDao, Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
fun ContactScreen(dao: ContactDao, modifier: Modifier = Modifier) {
    val contacts by remember(dao) { dao.getContacts() }.collectAsState(initial = emptyList())
    var contactText by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = contactText,
            onValueChange = { contactText = it },
            label = { Text("Contact") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val name = contactText.trim()
                if (name.isNotEmpty()) {
                    scope.launch { dao.insert(Contact(name = name)) }
                    contactText = ""
                }
            }, enabled = contactText.isNotBlank()) { Text("Add") }
            Button(onClick = { scope.launch { dao.deleteLastContact() } },
                enabled = contacts.isNotEmpty()) { Text("Delete last") }
        }
        Text("Contacts", style = MaterialTheme.typography.titleMedium)
        if (contacts.isEmpty()) Text("No contacts yet")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(contacts, key = { it.id }) { contact ->
                Text("${contact.id}. ${contact.name}")
            }
        }
    }
}
