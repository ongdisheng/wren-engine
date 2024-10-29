use std::collections::BTreeMap;
use std::fmt::Display;
use std::sync::Arc;

use serde::{Deserialize, Serialize};
use serde_with::serde_as;
use serde_with::NoneAsEmptyString;

/// This is the main struct that holds all the information about the manifest
#[derive(Serialize, Deserialize, Debug, PartialEq, Eq, Hash)]
pub struct Manifest {
    pub catalog: String,
    pub schema: String,
    #[serde(default)]
    pub models: Vec<Arc<Model>>,
    #[serde(default)]
    pub relationships: Vec<Arc<Relationship>>,
    #[serde(default)]
    pub metrics: Vec<Arc<Metric>>,
    #[serde(default)]
    pub views: Vec<Arc<View>>,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug, PartialEq, Eq, Hash)]
#[serde(rename_all = "camelCase")]
pub struct Model {
    pub name: String,
    #[serde(default)]
    pub ref_sql: Option<String>,
    #[serde(default)]
    pub base_object: Option<String>,
    #[serde(default, with = "table_reference")]
    pub table_reference: Option<String>,
    pub columns: Vec<Arc<Column>>,
    #[serde(default)]
    pub primary_key: Option<String>,
    #[serde(default, with = "bool_from_int")]
    pub cached: bool,
    #[serde(default)]
    pub refresh_time: Option<String>,
    #[serde(default)]
    pub properties: BTreeMap<String, String>,
}

impl Model {
    pub fn table_reference(&self) -> &str {
        self.table_reference.as_deref().unwrap_or("")
    }
}

mod table_reference {
    use serde::{self, Deserialize, Deserializer, Serialize, Serializer};

    #[derive(Deserialize, Serialize, Default)]
    struct TableReference {
        catalog: Option<String>,
        schema: Option<String>,
        table: Option<String>,
    }

    pub fn deserialize<'de, D>(deserializer: D) -> Result<Option<String>, D::Error>
    where
        D: Deserializer<'de>,
    {
        let TableReference {
            catalog,
            schema,
            table,
        } = TableReference::deserialize(deserializer)?;
        let mut result = String::new();
        if let Some(catalog) = catalog.filter(|c| !c.is_empty()) {
            result.push_str(&catalog);
            result.push('.');
        }
        if let Some(schema) = schema.filter(|s| !s.is_empty()) {
            result.push_str(&schema);
            result.push('.');
        }
        if let Some(table) = table.filter(|t| !t.is_empty()) {
            result.push_str(&table);
        }
        if result.is_empty() {
            Ok(None)
        } else {
            Ok(Some(result))
        }
    }

    pub fn serialize<S>(
        table_ref: &Option<String>,
        serializer: S,
    ) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        if let Some(table_ref) = table_ref {
            let parts: Vec<&str> =
                table_ref.split('.').filter(|p| !p.is_empty()).collect();
            if parts.len() > 3 {
                return Err(serde::ser::Error::custom(format!(
                    "Invalid table reference: {table_ref}"
                )));
            }
            let table_ref = if parts.len() == 3 {
                TableReference {
                    catalog: Some(parts[0].to_string()),
                    schema: Some(parts[1].to_string()),
                    table: Some(parts[2].to_string()),
                }
            } else if parts.len() == 2 {
                TableReference {
                    catalog: None,
                    schema: Some(parts[0].to_string()),
                    table: Some(parts[1].to_string()),
                }
            } else if parts.len() == 1 {
                TableReference {
                    catalog: None,
                    schema: None,
                    table: Some(parts[0].to_string()),
                }
            } else {
                TableReference::default()
            };
            table_ref.serialize(serializer)
        } else {
            TableReference::default().serialize(serializer)
        }
    }
}

mod bool_from_int {
    use serde::{self, Deserialize, Deserializer, Serialize, Serializer};

    pub fn deserialize<'de, D>(deserializer: D) -> Result<bool, D::Error>
    where
        D: Deserializer<'de>,
    {
        let value: serde_json::Value = Deserialize::deserialize(deserializer)?;
        match value {
            serde_json::Value::Bool(b) => Ok(b),
            // Backward compatibility with Wren AI manifests
            // In the legacy manifest format generated by Wren AI, boolean values are represented as integers (0 or 1)
            serde_json::Value::Number(n) if n.is_u64() => Ok(n.as_u64().unwrap() != 0),
            _ => Err(serde::de::Error::custom("invalid type for boolean")),
        }
    }

    pub fn serialize<S>(value: &bool, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        Serialize::serialize(value, serializer)
    }
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug, PartialEq, Eq, Hash)]
#[serde(rename_all = "camelCase")]
pub struct Column {
    pub name: String,
    pub r#type: String,
    #[serde(default)]
    pub relationship: Option<String>,
    #[serde(default, with = "bool_from_int")]
    pub is_calculated: bool,
    #[serde(default, with = "bool_from_int")]
    pub not_null: bool,
    #[serde_as(as = "NoneAsEmptyString")]
    #[serde(default)]
    pub expression: Option<String>,
    #[serde(default)]
    pub properties: BTreeMap<String, String>,
}

#[derive(Serialize, Deserialize, Debug, Hash, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Relationship {
    pub name: String,
    pub models: Vec<String>,
    pub join_type: JoinType,
    pub condition: String,
    #[serde(default)]
    pub properties: BTreeMap<String, String>,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, Eq, Hash, Clone, Copy)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum JoinType {
    #[serde(alias = "one_to_one")]
    OneToOne,
    #[serde(alias = "one_to_many")]
    OneToMany,
    #[serde(alias = "many_to_one")]
    ManyToOne,
    #[serde(alias = "many_to_many")]
    ManyToMany,
}

impl JoinType {
    pub fn is_to_one(&self) -> bool {
        matches!(self, JoinType::OneToOne | JoinType::ManyToOne)
    }
}

impl Display for JoinType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            JoinType::OneToOne => write!(f, "one_to_one"),
            JoinType::OneToMany => write!(f, "one_to_many"),
            JoinType::ManyToOne => write!(f, "many_to_one"),
            JoinType::ManyToMany => write!(f, "many_to_many"),
        }
    }
}

#[derive(Serialize, Deserialize, Debug, PartialEq, Eq, Hash)]
#[serde(rename_all = "camelCase")]
pub struct Metric {
    pub name: String,
    pub base_object: String,
    pub dimension: Vec<Arc<Column>>,
    pub measure: Vec<Arc<Column>>,
    pub time_grain: Vec<TimeGrain>,
    #[serde(default, with = "bool_from_int")]
    pub cached: bool,
    pub refresh_time: Option<String>,
    pub properties: BTreeMap<String, String>,
}

impl Metric {
    pub fn name(&self) -> &str {
        &self.name
    }
}

#[derive(Serialize, Deserialize, Debug, PartialEq, Eq, Hash)]
#[serde(rename_all = "camelCase")]
pub struct TimeGrain {
    pub name: String,
    pub ref_column: String,
    pub date_parts: Vec<TimeUnit>,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, Eq, Hash)]
pub enum TimeUnit {
    Year,
    Month,
    Day,
    Hour,
    Minute,
    Second,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, Eq, Hash)]
pub struct View {
    pub name: String,
    pub statement: String,
    #[serde(default)]
    pub properties: BTreeMap<String, String>,
}

impl View {
    pub fn name(&self) -> &str {
        &self.name
    }
}