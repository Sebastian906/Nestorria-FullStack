from pydantic import BaseModel, Field
from fastapi import APIRouter

from app.rag.llm import LLMClient

router = APIRouter(tags=["translate"])


class TranslateRequest(BaseModel):
    text: str = Field(max_length=2000)
    source: str = Field(pattern="^(en|es|auto)$")
    target: str = Field(pattern="^(en|es)$")


class TranslateResponse(BaseModel):
    translated: str
    source: str


@router.post("/translate", response_model=TranslateResponse)
async def translate(req: TranslateRequest) -> TranslateResponse:
    if not req.text.strip():
        return TranslateResponse(translated=req.text, source=req.target)

    source = req.source
    if source == "auto":
        source = await _detect(req.text)

    if source == req.target:
        return TranslateResponse(translated=req.text, source=source)

    if req.target == "es":
        style = "neutral Spanish (usted, no vosotros, no modismos)"
    else:
        style = "plain US English"

    llm = LLMClient()
    out = await llm.generate(
        [
            {
                "role": "system",
                "content": (
                    f"Translate {source} to {req.target} "
                    f"in {style}. Preserve numbers and dates. "
                    "Return only the translation."
                ),
            },
            {"role": "user", "content": req.text},
        ]
    )
    return TranslateResponse(translated=out.strip(), source=source)


async def _detect(text: str) -> str:
    llm = LLMClient()
    out = await llm.generate(
        [
            {
                "role": "system",
                "content": "Detect the language of the user text. Reply with exactly one word: en or es.",
            },
            {"role": "user", "content": text[:500]},
        ]
    )
    guess = out.strip().lower()
    return "es" if "es" in guess else "en"
