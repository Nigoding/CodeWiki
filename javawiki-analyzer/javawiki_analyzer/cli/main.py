from __future__ import annotations

from pathlib import Path
import shutil
import sys

import click


@click.group()
def cli() -> None:
    """JavaWiki Analyzer."""


@cli.command()
@click.argument("source")
@click.option(
    "--output",
    "-o",
    type=click.Path(path_type=Path),
    default=Path(".nanobot-analysis"),
    help="Output directory for nanobot analysis artifacts.",
)
@click.option(
    "--include",
    default="src/main/java/**/*.java",
    help="Comma-separated include globs relative to each Maven module.",
)
@click.option(
    "--exclude",
    default="src/test/**,target/**,.git/**",
    help="Comma-separated exclude globs.",
)
@click.option(
    "--keep-clone",
    is_flag=True,
    help="Keep a cloned remote repository under the output directory.",
)
@click.option(
    "--submodules",
    type=click.Choice(["none", "auto", "recursive"]),
    default="auto",
    show_default=True,
    help="How to handle Git submodules. Remote repositories initialize submodules in auto mode.",
)
@click.option(
    "--init-submodules",
    is_flag=True,
    help="Initialize Git submodules for a local repository. Local repositories are only inspected by default.",
)
@click.option("--verbose", "-v", is_flag=True, help="Show detailed progress.")
def analyze(
    source: str,
    output: Path,
    include: str,
    exclude: str,
    keep_clone: bool,
    submodules: str,
    init_submodules: bool,
    verbose: bool,
) -> None:
    """Analyze a local Java/Maven repo or HTTPS Git URL."""
    if shutil.which("git") is None and _looks_remote(source):
        click.secho("Git executable not found; remote repository analysis requires git.", fg="red", err=True)
        sys.exit(2)

    try:
        from javawiki_analyzer.pipeline import run_analysis

        result = run_analysis(
            source=source,
            output_dir=output,
            include_patterns=_split_csv(include),
            exclude_patterns=_split_csv(exclude),
            keep_clone=keep_clone,
            submodules=submodules,
            init_submodules=init_submodules,
            verbose=verbose,
        )
    except Exception as exc:
        click.secho(f"Analysis failed: {exc}", fg="red", err=True)
        if verbose:
            raise
        sys.exit(1)

    click.secho("Analysis complete", fg="green")
    click.echo(f"Output: {result}")


def _split_csv(value: str) -> list[str]:
    return [item.strip() for item in value.split(",") if item.strip()]


def _looks_remote(value: str) -> bool:
    return value.startswith("http://") or value.startswith("https://") or value.startswith("git@")


if __name__ == "__main__":
    cli()
