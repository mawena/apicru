<?php

namespace Mawena\Apicru\Traits;

use Illuminate\Support\Facades\Gate;
use Illuminate\Support\Facades\Response as FunctionType;
use Illuminate\Support\Facades\Validator;

trait ControllerHelperTrait
{

	/**
	 * Permet d'ajouter des filtres sur un objet Eloquent
	 * @param 	mixed 	$query				L'objet Eloquent
	 * @param 	mixed 	$filters			Les filtres
	 * @param 	mixed 	$values				Les valeurs à utiliser pour appliquer les filtres
	 * @return 	mixed
	 */
	public function queryFilter($query, $filters, $values)
	{
		foreach ($filters as $filter) {
			if (isset($values[$filter]) && $values[$filter]) {
				$query->where($filter, $values[$filter]);
			}
		}
		return $query;
	}

	/**
	 * Permet d'ajouter des filtres avec des valeurs multiples sur un objet Eloquent
	 * @param 	mixed 	$query				L'objet Eloquent
	 * @param 	mixed 	$filters			Les filtres
	 * @param 	mixed 	$values				Les valeurs à utiliser pour appliquer les filtres
	 * @param 	mixed 	$correlationData	Les valeurs à utiliser pour appliquer les filtres
	 * @return 	mixed
	 */
	public function queryMultipeValvueFilter($query, $associationFilters, $values, $correlationData)
	{
		foreach ($associationFilters as $filter => $chain) {
			if (isset($values[$filter]) && $values[$filter]) {
				$query->where(function ($query) use ($filter, $values, $correlationData) {
					foreach (explode("-", $values[$filter]) as $char) {
						if (array_key_exists($char, $correlationData)) {
							$query->where($filter, $correlationData[$char]);
						}
					}
				});
			}
		}
		return $query;
	}

	/**
	 * Permet d'ajouter des relation sur un objet Eloquent
	 * @param 	mixed 	$query				L'objet Eloquent
	 * @param 	mixed 	$relations			Les relations à ajouter
	 * @param 	mixed 	$values				Les valeurs à utiliser pour appliquer les relations
	 * @return 	mixed
	 */
	public function queryRelation($query, $relations, $values)
	{
		foreach ($relations as $relation => $model_relation) {
			if (isset($values[$relation]) && $values[$relation]) {
				$query->with($model_relation);
			}
		}
		return $query;
	}

	/**
	 * Permet d'ajouter un filtre de recherche sur un objet Eloquent
	 * @param 	mixed 	$query				L'objet Eloquent
	 * @param 	mixed 	$columns			Les colones où rechercher
	 * @param 	mixed 	$search				Le mot clé à rechercher
	 * @return 	mixed
	 */
	public function querySearch($query, $columns, $search)
	{
		$query
			->where(function ($query) use ($columns, $search) {
				foreach ($columns as $column) {
					$query->where($column, 'LIKE', "%$search%");
				}
			});
		return $query;
	}

	/**
	 * Permet d'ajouter des relation sur un model laravel via chargement load
	 * @param 	mixed 	$query				L'objet Eloquent
	 * @param 	mixed 	$relations			Les relations à ajouter
	 * @param 	mixed 	$values				Les valeurs à utiliser pour appliquer les relations
	 * @return 	mixed
	 */
	public function modelRelationLoad($model, $relations, $values)
	{
		$suplementList = [];
		foreach ($relations as $relation => $model_relation) {
			if (isset($$values[$relation]) && $values[$relation]) {
				$suplementList[] = $model_relation;
			}
		}
		$model->load($suplementList);
		return $model;
	}

	/**
	 * Vérifie si on a à faire à un base64 valide
	 * @param 	mixed	$base64				Le base64	
	 * @param 	mixed	$validateType		Les types de base64 valides
	 * @return	boolean
	 */
	public function checkIsBase64Validated($base64, $validatedTypes = ["pdf", "image"])
	{
		$validators = [
			"pdf" => fn($base64) => strpos($base64, 'data:application/pdf;base64,') === 0,
			"image" => fn($base64) => strpos($base64, 'data:image/') === 0,
		];
		foreach ($validatedTypes as $validatedType) {
			if ($validators[$validatedType]($base64)) {
				return true;
			}
		}
		return false;
	}
}
