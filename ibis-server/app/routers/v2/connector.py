from typing import Annotated

from fastapi import APIRouter, Depends, Query, Request, Response
from fastapi.responses import ORJSONResponse

from app.dependencies import verify_query_dto
from app.mdl.java_engine import JavaEngineConnector
from app.mdl.rewriter import Rewriter
from app.mdl.substitute import ModelSubstitute
from app.model import (
    DryPlanDTO,
    QueryDTO,
    TranspileDTO,
    ValidateDTO,
)
from app.model.connector import Connector
from app.model.data_source import DataSource
from app.model.metadata.dto import Constraint, MetadataDTO, Table
from app.model.metadata.factory import MetadataFactory
from app.model.validator import Validator
from app.util import to_json

router = APIRouter(prefix="/connector")


def get_java_engine_connector(request: Request) -> JavaEngineConnector:
    return request.state.java_engine_connector


@router.post("/{data_source}/query", dependencies=[Depends(verify_query_dto)])
async def query(
    data_source: DataSource,
    dto: QueryDTO,
    dry_run: Annotated[bool, Query(alias="dryRun")] = False,
    limit: int | None = None,
    java_engine_connector: JavaEngineConnector = Depends(get_java_engine_connector),
) -> Response:
    rewritten_sql = await Rewriter(
        dto.manifest_str,
        data_source=data_source,
        java_engine_connector=java_engine_connector,
    ).rewrite(dto.sql)
    connector = Connector(data_source, dto.connection_info)
    if dry_run:
        connector.dry_run(rewritten_sql)
        return Response(status_code=204)
    return ORJSONResponse(to_json(connector.query(rewritten_sql, limit=limit)))


@router.post("/{data_source}/validate/{rule_name}")
async def validate(
    data_source: DataSource,
    rule_name: str,
    dto: ValidateDTO,
    java_engine_connector: JavaEngineConnector = Depends(get_java_engine_connector),
) -> Response:
    validator = Validator(
        Connector(data_source, dto.connection_info),
        Rewriter(
            dto.manifest_str,
            data_source=data_source,
            java_engine_connector=java_engine_connector,
        ),
    )
    await validator.validate(rule_name, dto.parameters, dto.manifest_str)
    return Response(status_code=204)


@router.post("/{data_source}/metadata/tables", response_model=list[Table])
def get_table_list(data_source: DataSource, dto: MetadataDTO) -> list[Table]:
    return MetadataFactory.get_metadata(
        data_source, dto.connection_info
    ).get_table_list()


@router.post("/{data_source}/metadata/constraints", response_model=list[Constraint])
def get_constraints(data_source: DataSource, dto: MetadataDTO) -> list[Constraint]:
    return MetadataFactory.get_metadata(
        data_source, dto.connection_info
    ).get_constraints()


@router.post("/{data_source}/metadata/version")
def get_db_version(data_source: DataSource, dto: MetadataDTO) -> str:
    return MetadataFactory.get_metadata(data_source, dto.connection_info).get_version()


@router.post("/dry-plan")
async def dry_plan(
    dto: DryPlanDTO,
    java_engine_connector: JavaEngineConnector = Depends(get_java_engine_connector),
) -> str:
    return await Rewriter(
        dto.manifest_str, java_engine_connector=java_engine_connector
    ).rewrite(dto.sql)


@router.post("/{data_source}/dry-plan")
async def dry_plan_for_data_source(
    data_source: DataSource,
    dto: DryPlanDTO,
    java_engine_connector: JavaEngineConnector = Depends(get_java_engine_connector),
) -> str:
    return await Rewriter(
        dto.manifest_str,
        data_source=data_source,
        java_engine_connector=java_engine_connector,
    ).rewrite(dto.sql)


@router.post("/{data_source}/model-substitute")
async def model_substitute(
    data_source: DataSource,
    dto: TranspileDTO,
    java_engine_connector: JavaEngineConnector = Depends(get_java_engine_connector),
) -> str:
    sql = ModelSubstitute(data_source, dto.manifest_str).substitute(
        dto.sql, write="trino"
    )
    Connector(data_source, dto.connection_info).dry_run(
        await Rewriter(
            dto.manifest_str,
            data_source=data_source,
            java_engine_connector=java_engine_connector,
        ).rewrite(sql)
    )
    return sql
