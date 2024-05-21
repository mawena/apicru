<?php

namespace Mawena\Apicru\Traits;

use Illuminate\Support\Facades\Response as FunctionType;

trait CustomResponseTrait
{

	/**
	 * Formate le retour de façon générique pour les fonction index
	 * @param	mixed	$query			L'objet Eloquent
	 * @param	array	$requestData	Les données de la requête
	 * @param	string	$orderColumn	La colone de trie
	 * @param	int		$paginateCount	Le nombre d'élément par page
	 * @param	mixed	$status			Le statut applicatifs
	 * @param	array	$messages		Les messages applicatif
	 * @return	\Illuminate\Http\JsonResponse
	 */
	public function responseIndexOk($query, array $requestData, string $orderColumn = "updated_at", int $paginateCount = 8, $status = 200, array $messages = [])
	{
		if (isset($requestData["paginate"]) && $requestData["paginate"] == false) {
			$data = $query->orderByDesc($orderColumn)->get();
			$data = ["data" => $data, "total" => count($data)];
		} else {
			$data = $query->orderByDesc($orderColumn)->paginate($paginateCount)->toArray();
		}
		return $this->responseOkPaginate(data: $data, status: $status, messages: $messages);
	}

	/**
	 * Formate le retour pour le success
	 * @param	mixed	$data		Les données à envoyer
	 * @param	mixed	$messages	Les messages applicatifs
	 * @param	mixed	$status		Le statut applicatif
	 * @return	\Illuminate\Http\JsonResponse
	 */
	public function responseOk($data = [], $messages = [], $status = 200)
	{
		return FunctionType::json(
			[
				"status" => $status,
				"data" => $data,
				"messages" => $messages,
			],
		);
	}

	/**
	 * Formate le retour pour le success avec pagination
	 * @param	mixed	$data		Les données à envoyer
	 * @param	mixed	$messages	Les messages applicatifs
	 * @param	mixed	$status		Le statut applicatif
	 * @return	\Illuminate\Http\JsonResponse
	 */
	public function responseOkPaginate($data = [], $messages = [], $status = 200)
	{
		return FunctionType::json(
			array_merge(["status" => $status, "messages" => $messages], $data),
		);
	}

	/**
	 * Formate le retour pour les erreurs
	 * @param	mixed	$errors		Les erreurs applicatives
	 * @param	int		$status		Le statut HTTP
	 * @return	\Illuminate\Http\JsonResponse
	 */
	public function responseError($errors, int $status = 400)
	{
		return FunctionType::json(
			[
				"status" => $status,
				"errors" => $errors,
			],
			200,
		);
	}
}
